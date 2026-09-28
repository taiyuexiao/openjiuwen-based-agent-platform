package com.bosc.agentops.delivery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 模块 06 端到端集成测试：制品登记、部署目标（平台级 + 项目可选/默认）、分级结论、
 * 发布全链路（create→gate→approve→deploy→RUNNING）、四类门禁失败分支、审批前置校验、
 * 权限拟办分离、回滚语义、HOSTED 登记式部署、非法状态迁移 40902、审计落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeliveryIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String ADMIN = "u1002";
    private static final String DEVELOPER = "u1003";
    private static final String OUTSIDER = "u1004";
    private static final String OPERATOR = "u1006";

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;

    // ---------- 制品 ----------

    @Test
    void artifactRegisterImmutableAndTraceable() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        registerVersion(projectId, agentId, "1.0.0");

        ObjectNode req = objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", "1.0.0")
                .put("codeCommit", "abc123").put("imageDigest", "sha256:img1")
                .put("configDigest", "sha256:cfg1").put("evaluationRef", "EVAL-1");
        JsonNode registered = callApi("/v1/api/artifacts/register", OWNER, req);
        assertThat(registered.get("code").asInt()).as(String.valueOf(registered)).isEqualTo(0);
        long artifactId = registered.get("data").get("id").asLong();
        assertThat(registered.get("data").get("builtAt").asText()).isNotBlank();
        assertThat(registered.get("data").get("createdBy").asText()).isEqualTo(OWNER);

        // 重复登记同版本 → 40901（制品不可变）
        assertThat(callApi("/v1/api/artifacts/register", OWNER, req).get("code").asInt()).isEqualTo(40901);
        // 未登记的版本 → 40401（制品必须关联已登记版本，可追溯）
        assertThat(callApi("/v1/api/artifacts/register", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", "9.9.9"))
                .get("code").asInt()).isEqualTo(40401);
        // 跨项目冒用 → 40301
        long otherProjectId = createProject(OWNER);
        assertThat(callApi("/v1/api/artifacts/register", OWNER, objectMapper.createObjectNode()
                .put("projectId", otherProjectId).put("agentId", agentId).put("agentVersion", "1.0.1"))
                .get("code").asInt()).isEqualTo(40301);

        JsonNode detail = callApi("/v1/api/artifacts/detail", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", artifactId));
        assertThat(detail.get("data").get("imageDigest").asText()).isEqualTo("sha256:img1");
        JsonNode list = callApi("/v1/api/artifacts/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId));
        assertThat(list.get("data")).hasSize(1);
    }

    // ---------- 部署目标 ----------

    @Test
    void deployTargetCrudAndProjectAttach() throws Exception {
        long projectId = createProject(OWNER);
        long targetId = createTarget("UAT", List.of("P1", "P2"));

        // update
        JsonNode updated = callApi("/v1/api/deploy-targets/update", OWNER, objectMapper.createObjectNode()
                .put("id", targetId).put("name", "UAT 集群-改"));
        assertThat(updated.get("code").asInt()).as(String.valueOf(updated)).isEqualTo(0);
        assertThat(updated.get("data").get("name").asText()).isEqualTo("UAT 集群-改");

        // list（平台级）
        JsonNode list = callApi("/v1/api/deploy-targets/list", OWNER,
                objectMapper.createObjectNode().put("env", "UAT"));
        assertThat(list.get("code").asInt()).isEqualTo(0);
        assertThat(list.get("data").findValuesAsText("id")).contains(String.valueOf(targetId));

        // attach + set-default
        JsonNode attached = callApi("/v1/api/projects/" + projectId + "/deploy-targets/attach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId));
        assertThat(attached.get("code").asInt()).as(String.valueOf(attached)).isEqualTo(0);
        assertThat(attached.get("data").get("isDefault").asBoolean()).isFalse();
        // 重复挂接 → 40901
        assertThat(callApi("/v1/api/projects/" + projectId + "/deploy-targets/attach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId)).get("code").asInt()).isEqualTo(40901);
        JsonNode setDefault = callApi("/v1/api/projects/" + projectId + "/deploy-targets/set-default", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId));
        assertThat(setDefault.get("data").get("isDefault").asBoolean()).isTrue();
        JsonNode projectTargets = callApi("/v1/api/projects/" + projectId + "/deploy-targets/list", OWNER, null);
        assertThat(projectTargets.get("data")).hasSize(1);
        assertThat(projectTargets.get("data").get(0).get("target").get("env").asText()).isEqualTo("UAT");

        // disable 后禁止挂接到其他项目；update 被拒；重复 disable → 40902
        long otherProjectId = createProject(OWNER);
        assertThat(callApi("/v1/api/deploy-targets/disable", OWNER,
                objectMapper.createObjectNode().put("id", targetId)).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/projects/" + otherProjectId + "/deploy-targets/attach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId)).get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/deploy-targets/update", OWNER, objectMapper.createObjectNode()
                .put("id", targetId).put("name", "x")).get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/deploy-targets/disable", OWNER,
                objectMapper.createObjectNode().put("id", targetId)).get("code").asInt()).isEqualTo(40902);

        // detach
        assertThat(callApi("/v1/api/projects/" + projectId + "/deploy-targets/detach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId)).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/projects/" + projectId + "/deploy-targets/list", OWNER, null)
                .get("data")).isEmpty();

        // code 重复 → 40901
        String dupCode = "dl-t-dup-" + SEQ.incrementAndGet();
        assertThat(callApi("/v1/api/deploy-targets/create", OWNER, targetBody(dupCode, "SIT", null))
                .get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/deploy-targets/create", OWNER, targetBody(dupCode, "SIT", null))
                .get("code").asInt()).isEqualTo(40901);
        // 非法等级 → 40001
        assertThat(callApi("/v1/api/deploy-targets/create", OWNER,
                targetBody("dl-t-bad-" + SEQ.incrementAndGet(), "SIT", List.of("P9")))
                .get("code").asInt()).isEqualTo(40001);
    }

    // ---------- 分级结论 ----------

    @Test
    void agentLevelConfirmAndDisable() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");

        JsonNode p1 = callApi("/v1/api/agent-levels/confirm", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("level", "P1")
                .put("source", "2026Q3 投产评审会"));
        assertThat(p1.get("code").asInt()).as(String.valueOf(p1)).isEqualTo(0);
        assertThat(p1.get("data").get("effective").asBoolean()).isTrue();
        assertThat(p1.get("data").get("confirmedBy").asText()).isEqualTo(OWNER);

        // 新等级 confirm 后旧记录失效
        JsonNode p2 = callApi("/v1/api/agent-levels/confirm", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("level", "P2"));
        assertThat(p2.get("code").asInt()).isEqualTo(0);
        JsonNode list = callApi("/v1/api/agent-levels/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId));
        assertThat(list.get("data")).hasSize(2);
        assertThat(list.get("data").get(0).get("effective").asBoolean()).isFalse();
        assertThat(list.get("data").get(1).get("level").asText()).isEqualTo("P2");
        assertThat(list.get("data").get(1).get("effective").asBoolean()).isTrue();

        // disable 后无生效结论；重复 disable → 40401
        assertThat(callApi("/v1/api/agent-levels/disable", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId)).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/agent-levels/disable", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId)).get("code").asInt()).isEqualTo(40401);
    }

    // ---------- 发布全链路 ----------

    @Test
    void fullChainCreateGateApproveDeployRunning() throws Exception {
        long projectId = createProject(OWNER);
        addMember(projectId, OPERATOR, "OPERATOR");
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P1");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);

        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);
        JsonNode detail = releaseDetail(projectId, releaseId);
        assertThat(detail.get("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.get("levelSnapshot").asText()).isEqualTo("P1");

        // gate → GATED 且四项全过（gate 只检查不放行）
        JsonNode gated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        assertThat(gated.get("code").asInt()).as(String.valueOf(gated)).isEqualTo(0);
        assertThat(gated.get("data").get("status").asText()).isEqualTo("GATED");
        JsonNode gateResult = objectMapper.readTree(gated.get("data").get("gateResult").asText());
        assertThat(gateResult.get("passed").asBoolean()).isTrue();
        assertThat(gateResult.get("checks")).hasSize(4);
        gateResult.get("checks").forEach(c -> assertThat(c.get("passed").asBoolean()).isTrue());

        // approve → APPROVED，记录 approvalRef
        JsonNode approved = callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-" + SEQ.get()));
        assertThat(approved.get("code").asInt()).isEqualTo(0);
        assertThat(approved.get("data").get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("data").get("approvalRef").asText()).isEqualTo("FLOW-" + SEQ.get());

        // deploy → RUNNING；部署实例按 P1 保障配置（replicas=2），stub 生成伪 instance_url
        JsonNode deployed = callApi("/v1/api/releases/deploy", OWNER, idReq(projectId, releaseId));
        assertThat(deployed.get("code").asInt()).as(String.valueOf(deployed)).isEqualTo(0);
        assertThat(deployed.get("data").get("status").asText()).isEqualTo("RUNNING");

        JsonNode deployments = callApi("/v1/api/deployments/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("releaseId", releaseId));
        assertThat(deployments.get("data")).hasSize(1);
        JsonNode deployment = deployments.get("data").get(0);
        long deploymentId = deployment.get("id").asLong();
        assertThat(deployment.get("status").asText()).isEqualTo("RUNNING");
        assertThat(deployment.get("healthStatus").asText()).isEqualTo("HEALTHY");
        assertThat(deployment.get("executor").asText()).isEqualTo("LOCAL_STUB");
        assertThat(deployment.get("instanceUrl").asText()).isEqualTo("http://stub.local/" + deploymentId);
        assertThat(deployment.get("replicas").asInt()).isEqualTo(2);

        // 第二版发布上位后，旧部署自动停止（目标切换/版本更替留痕）
        registerVersion(projectId, agentId, "1.0.1");
        long artifactV2 = registerArtifact(projectId, agentId, "1.0.1", true);
        long releaseV2 = createRelease(projectId, agentId, "1.0.1", artifactV2, targetId);
        gateApproveDeploy(projectId, releaseV2);
        assertThat(releaseDetail(projectId, releaseV2).get("status").asText()).isEqualTo("RUNNING");
        JsonNode oldDeployment = callApi("/v1/api/deployments/detail", OWNER,
                idReq(projectId, deploymentId));
        assertThat(oldDeployment.get("data").get("status").asText()).isEqualTo("STOPPED");

        // deployments/stop：OPERATOR 有 delivery:deploy 权限；非 RUNNING 再停 → 40902
        JsonNode newDeployments = callApi("/v1/api/deployments/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("releaseId", releaseV2));
        long newDeploymentId = newDeployments.get("data").get(0).get("id").asLong();
        JsonNode stopped = callApi("/v1/api/deployments/stop", OPERATOR, idReq(projectId, newDeploymentId));
        assertThat(stopped.get("code").asInt()).as(String.valueOf(stopped)).isEqualTo(0);
        assertThat(stopped.get("data").get("status").asText()).isEqualTo("STOPPED");
        assertThat(callApi("/v1/api/deployments/stop", OPERATOR, idReq(projectId, newDeploymentId))
                .get("code").asInt()).isEqualTo(40902);
    }

    // ---------- 门禁四项失败分支 ----------

    @Test
    void gateFailsWithoutAppBinding() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);

        JsonNode gated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        assertThat(gated.get("code").asInt()).isEqualTo(0);
        assertThat(gated.get("data").get("status").asText()).isEqualTo("GATED");
        JsonNode gateResult = objectMapper.readTree(gated.get("data").get("gateResult").asText());
        assertThat(gateResult.get("passed").asBoolean()).isFalse();
        JsonNode bindingCheck = findCheck(gateResult, "appBinding");
        assertThat(bindingCheck.get("passed").asBoolean()).isFalse();
        assertThat(bindingCheck.get("reasons").toString()).contains("归属");

        // 门禁未过，approve 被拒（40902）
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-X"))
                .get("code").asInt()).isEqualTo(40902);

        // 修复（绑定归属）后重新 gate 通过：GATED → GATED
        bindApp(projectId, agentId);
        JsonNode regated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        JsonNode regateResult = objectMapper.readTree(regated.get("data").get("gateResult").asText());
        assertThat(regateResult.get("passed").asBoolean()).isTrue();

        // gate 失败也落审计（result=FAIL）
        List<AuditEvent> gateFails = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "delivery")
                .eq(AuditEvent::getAction, "release.gate")
                .eq(AuditEvent::getResourceId, String.valueOf(releaseId))
                .eq(AuditEvent::getResult, "FAIL"));
        assertThat(gateFails).hasSize(1);
    }

    @Test
    void gateFailsOnIncompleteArtifact() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        // 缺 configDigest 与 evaluationRef
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", false);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);

        JsonNode gated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        JsonNode gateResult = objectMapper.readTree(gated.get("data").get("gateResult").asText());
        assertThat(gateResult.get("passed").asBoolean()).isFalse();
        JsonNode artifactCheck = findCheck(gateResult, "artifact");
        assertThat(artifactCheck.get("passed").asBoolean()).isFalse();
        assertThat(artifactCheck.get("reasons").toString())
                .contains("configDigest").contains("evaluationRef");
        assertThat(releaseDetail(projectId, releaseId).get("status").asText()).isEqualTo("GATED");
    }

    @Test
    void gateFailsWithoutAgentLevel() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);
        // 无分级结论：create 时 levelSnapshot 为空
        assertThat(releaseDetail(projectId, releaseId).get("levelSnapshot").isNull()).isTrue();

        JsonNode gated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        JsonNode gateResult = objectMapper.readTree(gated.get("data").get("gateResult").asText());
        assertThat(gateResult.get("passed").asBoolean()).isFalse();
        assertThat(findCheck(gateResult, "agentLevel").get("passed").asBoolean()).isFalse();
    }

    @Test
    void gateFailsOnIllegalTarget() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P1");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);

        // 目标未挂接到项目 → 不在可选集合
        long unattachedTargetId = createTarget("UAT", null);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, unattachedTargetId);
        JsonNode gated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId));
        JsonNode gateResult = objectMapper.readTree(gated.get("data").get("gateResult").asText());
        assertThat(gateResult.get("passed").asBoolean()).isFalse();
        assertThat(findCheck(gateResult, "deployTarget").get("reasons").toString()).contains("可选集合");

        // PROD 目标 + 等级不在 allowedAgentLevels → 拒绝；改评审等级 P0 后通过
        long prodTargetId = createTarget("PROD", List.of("P0"));
        attach(projectId, prodTargetId);
        long prodReleaseId = createRelease(projectId, agentId, "1.0.0", artifactId, prodTargetId);
        JsonNode prodGated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, prodReleaseId));
        JsonNode prodGateResult = objectMapper.readTree(prodGated.get("data").get("gateResult").asText());
        assertThat(prodGateResult.get("passed").asBoolean()).isFalse();
        assertThat(findCheck(prodGateResult, "deployTarget").get("reasons").toString())
                .contains("P1").contains("P0");

        confirmLevel(projectId, agentId, "P0");
        JsonNode regated = callApi("/v1/api/releases/gate", OWNER, idReq(projectId, prodReleaseId));
        JsonNode regateResult = objectMapper.readTree(regated.get("data").get("gateResult").asText());
        assertThat(regateResult.get("passed").asBoolean()).isTrue();
        assertThat(regated.get("data").get("levelSnapshot").asText()).isEqualTo("P0");
    }

    // ---------- 审批前置 ----------

    @Test
    void approveRequiresGateAndApprovalRef() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);

        // 未 gate 直接 approve → 40902
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-1"))
                .get("code").asInt()).isEqualTo(40902);
        // 无 approvalRef → 40001
        assertThat(callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId)).get("code").asInt()).isEqualTo(40001);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", " "))
                .get("code").asInt()).isEqualTo(40001);
        // 正常放行
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-1"))
                .get("code").asInt()).isEqualTo(0);
    }

    // ---------- 权限拟办分离 ----------

    @Test
    void approvePermissionSeparatedFromRelease() throws Exception {
        long projectId = createProject(OWNER);
        addMember(projectId, ADMIN, "ADMIN");
        addMember(projectId, DEVELOPER, "DEVELOPER");
        addMember(projectId, OPERATOR, "OPERATOR");
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);

        // DEVELOPER 可 create + gate release
        JsonNode created = callApi("/v1/api/releases/create", DEVELOPER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", "1.0.0")
                .put("artifactId", artifactId).put("targetId", targetId));
        assertThat(created.get("code").asInt()).as(String.valueOf(created)).isEqualTo(0);
        long releaseId = created.get("data").get("id").asLong();
        assertThat(callApi("/v1/api/releases/gate", DEVELOPER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(0);

        // 拟办分离：DEVELOPER / ADMIN（无 delivery:approve）approve → 40301；仅 OWNER 可放行
        assertThat(callApi("/v1/api/releases/approve", DEVELOPER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-9"))
                .get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/releases/approve", ADMIN, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-9"))
                .get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "FLOW-9"))
                .get("code").asInt()).isEqualTo(0);

        // deploy 权限：DEVELOPER（无 delivery:deploy）→ 40301；OPERATOR（运维执行）放行
        assertThat(callApi("/v1/api/releases/deploy", DEVELOPER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/releases/deploy", OPERATOR, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(0);
        assertThat(releaseDetail(projectId, releaseId).get("status").asText()).isEqualTo("RUNNING");

        // 非成员 fail-closed；OPERATOR 无 delivery:artifact
        assertThat(callApi("/v1/api/releases/list", OUTSIDER, objectMapper.createObjectNode()
                .put("projectId", projectId)).get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/artifacts/register", OPERATOR, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", "1.0.2"))
                .get("code").asInt()).isEqualTo(40301);
    }

    // ---------- 回滚 ----------

    @Test
    void rollbackCreatesNewReleaseAndMarksOldRolledBack() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactV1 = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseV1 = createRelease(projectId, agentId, "1.0.0", artifactV1, targetId);
        gateApproveDeploy(projectId, releaseV1);
        assertThat(releaseDetail(projectId, releaseV1).get("status").asText()).isEqualTo("RUNNING");

        registerVersion(projectId, agentId, "1.0.1");
        long artifactV2 = registerArtifact(projectId, agentId, "1.0.1", true);
        long releaseV2 = createRelease(projectId, agentId, "1.0.1", artifactV2, targetId);
        gateApproveDeploy(projectId, releaseV2);

        // rollback：生成指向上一 RUNNING（releaseV1 快照组合）的新 Release，旧单置 ROLLED_BACK
        JsonNode rollback = callApi("/v1/api/releases/rollback", OWNER, idReq(projectId, releaseV2));
        assertThat(rollback.get("code").asInt()).as(String.valueOf(rollback)).isEqualTo(0);
        JsonNode rollbackRelease = rollback.get("data");
        long rollbackReleaseId = rollbackRelease.get("id").asLong();
        assertThat(rollbackRelease.get("status").asText()).isEqualTo("DRAFT");
        assertThat(rollbackRelease.get("artifactId").asLong()).isEqualTo(artifactV1);
        assertThat(rollbackRelease.get("targetId").asLong()).isEqualTo(targetId);
        assertThat(rollbackRelease.get("agentVersion").asText()).isEqualTo("1.0.0");
        assertThat(rollbackRelease.get("levelSnapshot").asText()).isEqualTo("P2");
        assertThat(rollbackRelease.get("rollbackOf").asLong()).isEqualTo(releaseV2);
        assertThat(releaseDetail(projectId, releaseV2).get("status").asText()).isEqualTo("ROLLED_BACK");

        // 回滚单重新走 gate→approve→deploy 全链路
        gateApproveDeploy(projectId, rollbackReleaseId);
        assertThat(releaseDetail(projectId, rollbackReleaseId).get("status").asText()).isEqualTo("RUNNING");

        // 前置校验：不存在上一个 RUNNING 时拒绝（当前仅回滚单 RUNNING，其上一 RUNNING 是 releaseV1 → 允许；
        // 再造一个无历史的 Agent 验证拒绝分支）
        long loneAgentId = createAgent(projectId, "ADAPTED");
        bindApp(projectId, loneAgentId);
        confirmLevel(projectId, loneAgentId, "P3");
        registerVersion(projectId, loneAgentId, "1.0.0");
        long loneArtifact = registerArtifact(projectId, loneAgentId, "1.0.0", true);
        long loneRelease = createRelease(projectId, loneAgentId, "1.0.0", loneArtifact, targetId);
        gateApproveDeploy(projectId, loneRelease);
        assertThat(releaseDetail(projectId, loneRelease).get("status").asText()).isEqualTo("RUNNING");
        JsonNode noPrevious = callApi("/v1/api/releases/rollback", OWNER, idReq(projectId, loneRelease));
        assertThat(noPrevious.get("code").asInt()).isEqualTo(40902);
        assertThat(noPrevious.get("message").asText()).contains("RUNNING");
    }

    // ---------- HOSTED 托管部署 ----------

    @Test
    void hostedAgentDeployRegistersExternalDeployment() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "HOSTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P3");
        // HOSTED 不登记 AgentVersion，制品按版本号标签登记
        long artifactId = registerArtifact(projectId, agentId, "2026.09", true);
        long releaseId = createRelease(projectId, agentId, "2026.09", artifactId, targetId);
        gateApproveDeploy(projectId, releaseId);
        assertThat(releaseDetail(projectId, releaseId).get("status").asText()).isEqualTo("RUNNING");

        JsonNode deployments = callApi("/v1/api/deployments/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("releaseId", releaseId));
        assertThat(deployments.get("data")).hasSize(1);
        JsonNode deployment = deployments.get("data").get(0);
        assertThat(deployment.get("executor").asText()).isEqualTo("HOSTED_EXTERNAL");
        assertThat(deployment.get("instanceUrl").asText()).isEqualTo("http://hosted.example.com/agent");
        assertThat(deployment.get("healthStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(deployment.get("status").asText()).isEqualTo("RUNNING");
        assertThat(deployment.get("replicas").isNull()).isTrue();
    }

    // ---------- 非法状态迁移 ----------

    @Test
    void illegalStateTransitionsRejected() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);

        // DRAFT：deploy/approve/rollback 全部 40902
        assertThat(callApi("/v1/api/releases/deploy", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "F"))
                .get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/releases/rollback", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(40902);

        // RUNNING 后：gate/approve/deploy 均 40902
        gateApproveDeploy(projectId, releaseId);
        assertThat(callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId).put("approvalRef", "F"))
                .get("code").asInt()).isEqualTo(40902);
        assertThat(callApi("/v1/api/releases/deploy", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(40902);

        // 制品与版本不一致 → 40001
        long otherAgent = createAgent(projectId, "ADAPTED");
        registerVersion(projectId, otherAgent, "1.0.0");
        long otherArtifact = registerArtifact(projectId, otherAgent, "1.0.0", true);
        assertThat(callApi("/v1/api/releases/create", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", "1.0.0")
                .put("artifactId", otherArtifact).put("targetId", targetId))
                .get("code").asInt()).isEqualTo(40001);
    }

    // ---------- 审计 ----------

    @Test
    void auditEventsRecordedWithStateTransitions() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(projectId, "ADAPTED");
        long targetId = createTarget("UAT", null);
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, "1.0.0");
        long artifactId = registerArtifact(projectId, agentId, "1.0.0", true);
        long releaseId = createRelease(projectId, agentId, "1.0.0", artifactId, targetId);
        gateApproveDeploy(projectId, releaseId);

        List<AuditEvent> events = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "delivery")
                .eq(AuditEvent::getResourceType, "delivery_release")
                .eq(AuditEvent::getResourceId, String.valueOf(releaseId))
                .orderByAsc(AuditEvent::getId));
        List<String> actions = events.stream().map(AuditEvent::getAction).toList();
        assertThat(actions).containsSubsequence("release.create", "release.gate", "release.approve",
                "release.deploy", "release.running");
        assertThat(events).allMatch(e -> "delivery_release".equals(e.getResourceType()));
        // 状态迁移留痕：from → to
        AuditEvent approve = events.stream()
                .filter(e -> "release.approve".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(approve.getDetail()).contains("\"fromStatus\":\"GATED\"")
                .contains("\"toStatus\":\"APPROVED\"").contains("approvalRef");
        AuditEvent running = events.stream()
                .filter(e -> "release.running".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(running.getDetail()).contains("\"fromStatus\":\"DEPLOYING\"")
                .contains("\"toStatus\":\"RUNNING\"");

        // 制品/目标/等级写操作同样落审计
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "delivery")
                .eq(AuditEvent::getAction, "artifact.register")
                .eq(AuditEvent::getResourceId, String.valueOf(artifactId)))).isEqualTo(1);
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "delivery")
                .eq(AuditEvent::getAction, "level.confirm"))).isGreaterThanOrEqualTo(1);
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "delivery")
                .eq(AuditEvent::getAction, "target.attach"))).isGreaterThanOrEqualTo(1);
    }

    // ---------- helpers ----------

    private JsonNode findCheck(JsonNode gateResult, String name) {
        for (JsonNode check : gateResult.get("checks")) {
            if (name.equals(check.get("name").asText())) {
                return check;
            }
        }
        throw new AssertionError("gateResult 中无检查项: " + name + " => " + gateResult);
    }

    private ObjectNode idReq(long projectId, long id) {
        return objectMapper.createObjectNode().put("projectId", projectId).put("id", id);
    }

    private JsonNode releaseDetail(long projectId, long releaseId) throws Exception {
        JsonNode detail = callApi("/v1/api/releases/detail", OWNER, idReq(projectId, releaseId));
        assertThat(detail.get("code").asInt()).as(String.valueOf(detail)).isEqualTo(0);
        return detail.get("data");
    }

    private void gateApproveDeploy(long projectId, long releaseId) throws Exception {
        assertThat(callApi("/v1/api/releases/gate", OWNER, idReq(projectId, releaseId))
                .get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId)
                .put("approvalRef", "FLOW-" + SEQ.incrementAndGet())).get("code").asInt()).isEqualTo(0);
        JsonNode deployed = callApi("/v1/api/releases/deploy", OWNER, idReq(projectId, releaseId));
        assertThat(deployed.get("code").asInt()).as(String.valueOf(deployed)).isEqualTo(0);
    }

    private long createProject(String userId) throws Exception {
        JsonNode response = callApi("/v1/api/projects/create", userId, objectMapper.createObjectNode()
                .put("code", "dl-p" + SEQ.incrementAndGet()).put("name", "交付测试项目"));
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void addMember(long projectId, String subjectId, String role) throws Exception {
        JsonNode response = callApi("/v1/api/projects/" + projectId + "/members/add", OWNER,
                objectMapper.createObjectNode().put("subjectType", "USER")
                        .put("subjectId", subjectId).put("role", role));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long createAgent(long projectId, String accessMode) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "dl-ag-" + SEQ.incrementAndGet())
                .put("name", "交付测试 Agent")
                .put("projectId", projectId)
                .put("accessMode", accessMode);
        if ("HOSTED".equals(accessMode)) {
            body.put("runtimeEndpoint", "http://hosted.example.com/agent");
            body.put("healthEndpoint", "http://hosted.example.com/agent/health");
        }
        JsonNode response = callApi("/v1/api/agents/create", OWNER, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void registerVersion(long projectId, long agentId, String version) throws Exception {
        // ADAPTED 空声明登记（能力降级），满足制品-版本追溯约束
        JsonNode response = callApi("/v1/api/agents/" + agentId + "/versions/register", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", version));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private void bindApp(long projectId, long agentId) throws Exception {
        JsonNode response = callApi("/v1/api/bindings/bind", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("appCode", "APP-CORE-001"));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long registerArtifact(long projectId, long agentId, String version, boolean complete)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", version)
                .put("codeCommit", "c" + SEQ.incrementAndGet())
                .put("imageDigest", "sha256:" + SEQ.incrementAndGet());
        if (complete) {
            body.put("configDigest", "sha256:cfg" + SEQ.incrementAndGet());
            body.put("evaluationRef", "EVAL-" + SEQ.incrementAndGet());
        }
        JsonNode response = callApi("/v1/api/artifacts/register", OWNER, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private ObjectNode targetBody(String code, String env, List<String> allowedLevels) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", code).put("name", "目标-" + code)
                .put("env", env).put("cluster", "k8s-" + env.toLowerCase())
                .put("namespace", "agentops");
        body.putObject("baseResource").put("cpu", "1").put("memory", "2Gi");
        if (allowedLevels != null) {
            com.fasterxml.jackson.databind.node.ArrayNode levels = body.putArray("allowedAgentLevels");
            allowedLevels.forEach(levels::add);
        }
        return body;
    }

    private long createTarget(String env, List<String> allowedLevels) throws Exception {
        JsonNode response = callApi("/v1/api/deploy-targets/create", OWNER,
                targetBody("dl-t-" + SEQ.incrementAndGet(), env, allowedLevels));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void attach(long projectId, long targetId) throws Exception {
        JsonNode response = callApi("/v1/api/projects/" + projectId + "/deploy-targets/attach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private void confirmLevel(long projectId, long agentId, String level) throws Exception {
        JsonNode response = callApi("/v1/api/agent-levels/confirm", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("level", level)
                .put("source", "评审单-" + SEQ.incrementAndGet()));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long createRelease(long projectId, long agentId, String version, long artifactId, long targetId)
            throws Exception {
        JsonNode response = callApi("/v1/api/releases/create", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", version)
                .put("artifactId", artifactId).put("targetId", targetId));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + userId);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
