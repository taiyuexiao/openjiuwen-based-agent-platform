package com.bosc.agentops.enterprise;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.enterprise.connector.CmdbApp;
import com.bosc.agentops.enterprise.connector.CmdbAppStatus;
import com.bosc.agentops.enterprise.connector.MockCmdbConnector;
import com.bosc.agentops.enterprise.entity.AppBinding;
import com.bosc.agentops.enterprise.entity.SyncStatus;
import com.bosc.agentops.enterprise.gate.AppBindingGate;
import com.bosc.agentops.enterprise.gate.GateResult;
import com.bosc.agentops.enterprise.mapper.AppBindingMapper;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 模块 09 端到端集成测试：归属绑定（快照/负向/重复绑定/归档 Agent）、重同步（刷新/应用下线/CMDB 查无）、
 * 投产门禁（无绑定/FAILED/STALE/缺分级/通过）、权限拦截、审计落库、ITSM 登记。
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnterpriseIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String DEVELOPER = "u1003";
    private static final String OUTSIDER = "u1004";
    private static final String OPERATOR = "u1006";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppBindingMapper bindingMapper;
    @Autowired
    private MockCmdbConnector mockCmdbConnector;
    @Autowired
    private AppBindingGate appBindingGate;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void bindSuccessWritesFullSnapshot() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();

        JsonNode resp = bind(OWNER, projectId, agentId, "APP-CORE-001");
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        JsonNode data = resp.get("data");
        assertThat(data.get("appCode").asText()).isEqualTo("APP-CORE-001");
        assertThat(data.get("sourceSystem").asText()).isEqualTo("MOCK_CMDB");
        assertThat(data.get("syncStatus").asText()).isEqualTo("SYNCED");
        assertThat(data.get("effectiveSyncStatus").asText()).isEqualTo("SYNCED");
        assertThat(data.get("syncedAt").asText()).isNotBlank();
        assertThat(data.get("boundBy").asText()).isEqualTo(OWNER);
        JsonNode snapshot = objectMapper.readTree(data.get("snapshot").asText());
        assertThat(snapshot.get("appName").asText()).isEqualTo("核心账务系统");
        assertThat(snapshot.get("owner").asText()).isEqualTo("张核心");
        assertThat(snapshot.get("bizDomain").asText()).isEqualTo("账务域");
        assertThat(snapshot.get("appLevel").asText()).isEqualTo("A");

        AppBinding stored = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        assertThat(stored.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(stored.getSyncedAt()).isNotNull();

        JsonNode detail = callApi("/v1/api/bindings/detail", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        assertThat(detail.get("data").get("appCode").asText()).isEqualTo("APP-CORE-001");
        JsonNode list = callApi("/v1/api/bindings/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(list.get("data")).hasSize(1);
    }

    @Test
    void bindRejectsDisabledAndUnknownApp() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();

        // 应用已下线（DISABLED）→ 40902
        JsonNode disabled = bind(OWNER, projectId, agentId, "APP-LEGACY-099");
        assertThat(disabled.get("code").asInt()).isEqualTo(40902);
        assertThat(disabled.get("message").asText()).contains("APP-LEGACY-099");

        // CMDB 查无应用 → 40401（fail-closed）
        JsonNode unknown = bind(OWNER, projectId, agentId, "APP-NOT-EXIST");
        assertThat(unknown.get("code").asInt()).isEqualTo(40401);

        assertThat(bindingMapper.selectList(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId))).isEmpty();
    }

    @Test
    void duplicateBindRejectedAndUnbindAllowsRebind() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        assertThat(bind(OWNER, projectId, agentId, "APP-CORE-001").get("code").asInt()).isEqualTo(0);

        // 重复绑定 → 40901，需先 unbind
        JsonNode dup = bind(OWNER, projectId, agentId, "APP-CRM-002");
        assertThat(dup.get("code").asInt()).isEqualTo(40901);
        assertThat(dup.get("message").asText()).contains("unbind");

        ObjectNode operate = objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId);
        JsonNode unbind = callApi("/v1/api/bindings/unbind", OWNER, operate);
        assertThat(unbind.get("code").asInt()).isEqualTo(0);
        assertThat(bindingMapper.selectList(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId))).isEmpty();

        // 解绑后可改绑其他应用
        JsonNode rebind = bind(OWNER, projectId, agentId, "APP-CRM-002");
        assertThat(rebind.get("code").asInt()).isEqualTo(0);
        assertThat(rebind.get("data").get("appCode").asText()).isEqualTo("APP-CRM-002");

        // 未绑定状态下 unbind/resync → 40401
        long freeAgentId = createAgent(OWNER, projectId).andGetId();
        ObjectNode freeOperate = objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", freeAgentId);
        assertThat(callApi("/v1/api/bindings/unbind", OWNER, freeOperate).get("code").asInt()).isEqualTo(40401);
        assertThat(callApi("/v1/api/bindings/resync", OWNER, freeOperate).get("code").asInt()).isEqualTo(40401);
    }

    @Test
    void bindRejectsArchivedOrMissingAgent() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        callApi("/v1/api/agents/archive", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));

        JsonNode archived = bind(OWNER, projectId, agentId, "APP-CORE-001");
        assertThat(archived.get("code").asInt()).isEqualTo(40902);
        assertThat(archived.get("message").asText()).contains("ACTIVE");

        JsonNode missing = bind(OWNER, projectId, 999999L, "APP-CORE-001");
        assertThat(missing.get("code").asInt()).isEqualTo(40401);

        // 跨项目冒用：agent 不属于该项目 → 40301
        long otherProjectId = createProject(OWNER);
        long otherAgentId = createAgent(OWNER, otherProjectId).andGetId();
        JsonNode crossProject = bind(OWNER, projectId, otherAgentId, "APP-CORE-001");
        assertThat(crossProject.get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void resyncRefreshesSnapshotAndSyncedAt() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String appCode = registerMockApp("支付清算系统", "钱清算", CmdbAppStatus.ACTIVE);
        assertThat(bind(OWNER, projectId, agentId, appCode).get("code").asInt()).isEqualTo(0);

        // 构造旧 synced_at（72h 前）→ 有效状态动态判定为 STALE
        backdateSyncedAt(agentId, 72);
        JsonNode staleDetail = callApi("/v1/api/bindings/detail", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        assertThat(staleDetail.get("data").get("syncStatus").asText()).isEqualTo("SYNCED");
        assertThat(staleDetail.get("data").get("effectiveSyncStatus").asText()).isEqualTo("STALE");

        // CMDB 侧 Owner 变更后重同步 → 快照刷新、synced_at 刷新、回到 SYNCED
        mockCmdbConnector.putApp(new CmdbApp(appCode, "支付清算系统", "孙清算", "支付域", "B",
                CmdbAppStatus.ACTIVE));
        JsonNode resync = callApi("/v1/api/bindings/resync", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        assertThat(resync.get("code").asInt()).as(String.valueOf(resync)).isEqualTo(0);
        JsonNode data = resync.get("data");
        assertThat(data.get("syncStatus").asText()).isEqualTo("SYNCED");
        assertThat(data.get("effectiveSyncStatus").asText()).isEqualTo("SYNCED");
        assertThat(data.get("warning").isNull()).isTrue();
        LocalDateTime syncedAt = LocalDateTime.parse(data.get("syncedAt").asText());
        assertThat(syncedAt).isAfter(LocalDateTime.now().minusMinutes(1));
        JsonNode snapshot = objectMapper.readTree(data.get("snapshot").asText());
        assertThat(snapshot.get("owner").asText()).isEqualTo("孙清算");
        assertThat(snapshot.get("appLevel").asText()).isEqualTo("B");
    }

    @Test
    void resyncAppDisabledMarksStaleAndKeepsSnapshot() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String appCode = registerMockApp("授信审批系统", "周授信", CmdbAppStatus.ACTIVE);
        JsonNode bound = bind(OWNER, projectId, agentId, appCode);
        String snapshotBefore = bound.get("data").get("snapshot").asText();

        // CMDB 侧应用下线（DISABLED）→ resync 置 STALE、保留旧快照、返回告警
        mockCmdbConnector.putApp(new CmdbApp(appCode, "授信审批系统", "周授信", "授信域", "B",
                CmdbAppStatus.DISABLED));
        JsonNode resync = callApi("/v1/api/bindings/resync", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        assertThat(resync.get("code").asInt()).as(String.valueOf(resync)).isEqualTo(0);
        assertThat(resync.get("data").get("syncStatus").asText()).isEqualTo("STALE");
        assertThat(resync.get("data").get("effectiveSyncStatus").asText()).isEqualTo("STALE");
        assertThat(resync.get("data").get("warning").asText()).contains("停用");
        assertThat(resync.get("data").get("snapshot").asText()).isEqualTo(snapshotBefore);

        AppBinding stored = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        assertThat(stored.getSyncStatus()).isEqualTo(SyncStatus.STALE);
        assertThat(stored.getSnapshot()).isEqualTo(snapshotBefore);
    }

    @Test
    void resyncAppMissingMarksFailedAndKeepsSnapshot() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String appCode = registerMockApp("渠道整合系统", "吴渠道", CmdbAppStatus.ACTIVE);
        JsonNode bound = bind(OWNER, projectId, agentId, appCode);
        assertThat(bound.get("code").asInt()).as(String.valueOf(bound)).isEqualTo(0);
        AppBinding before = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));

        // CMDB 侧应用被删除 → resync 置 FAILED、保留旧快照与 synced_at、返回告警
        mockCmdbConnector.removeApp(appCode);
        JsonNode resync = callApi("/v1/api/bindings/resync", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        assertThat(resync.get("code").asInt()).as(String.valueOf(resync)).isEqualTo(0);
        assertThat(resync.get("data").get("syncStatus").asText()).isEqualTo("FAILED");
        assertThat(resync.get("data").get("warning").asText()).contains("FAILED");

        AppBinding after = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        assertThat(after.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(after.getSnapshot()).isEqualTo(before.getSnapshot());
        assertThat(after.getSyncedAt()).isEqualTo(before.getSyncedAt());
    }

    @Test
    void gateCoversAllBranches() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String appCode = registerMockApp("理财销售系统", "郑理财", CmdbAppStatus.ACTIVE);

        // 无绑定 → fail「未绑定归属应用」
        GateResult unbound = appBindingGate.validateForRelease(agentId);
        assertThat(unbound.isPassed()).isFalse();
        assertThat(unbound.getReasons()).anyMatch(r -> r.contains("未绑定归属应用"));

        // 绑定且快照完整 → pass
        assertThat(bind(OWNER, projectId, agentId, appCode).get("code").asInt()).isEqualTo(0);
        GateResult passed = appBindingGate.validateForRelease(agentId);
        assertThat(passed.isPassed()).as(String.valueOf(passed.getReasons())).isTrue();
        assertThat(passed.getReasons()).isEmpty();

        // STALE（synced_at 超阈值）→ fail「归属信息过期」
        backdateSyncedAt(agentId, 72);
        GateResult stale = appBindingGate.validateForRelease(agentId);
        assertThat(stale.isPassed()).isFalse();
        assertThat(stale.getReasons()).anyMatch(r -> r.contains("过期"));

        // 重同步恢复 pass
        assertThat(callApi("/v1/api/bindings/resync", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId)).get("code").asInt()).isEqualTo(0);
        assertThat(appBindingGate.validateForRelease(agentId).isPassed()).isTrue();

        // 快照缺 appLevel → fail「缺少应用分级」
        AppBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        binding.setSnapshot("{\"appCode\":\"" + appCode + "\",\"appName\":\"理财销售系统\"}");
        bindingMapper.updateById(binding);
        GateResult noLevel = appBindingGate.validateForRelease(agentId);
        assertThat(noLevel.isPassed()).isFalse();
        assertThat(noLevel.getReasons()).anyMatch(r -> r.contains("分级"));

        // FAILED（CMDB 查无应用后重同步）→ fail「同步失败」
        assertThat(callApi("/v1/api/bindings/resync", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId)).get("code").asInt()).isEqualTo(0);
        mockCmdbConnector.removeApp(appCode);
        callApi("/v1/api/bindings/resync", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId));
        GateResult failed = appBindingGate.validateForRelease(agentId);
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.getReasons()).anyMatch(r -> r.contains("FAILED"));
    }

    @Test
    void bindingEndpointsEnforceProjectPermission() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, DEVELOPER, "DEVELOPER").andExpectCode(0);
        addMember(OWNER, projectId, OPERATOR, "OPERATOR").andExpectCode(0);
        long agentId = createAgent(OWNER, projectId).andGetId();
        assertThat(bind(OWNER, projectId, agentId, "APP-CORE-001").get("code").asInt()).isEqualTo(0);

        // 非成员（fail-closed）与 DEVELOPER/OPERATOR（仅 binding:read）：bind/resync/unbind 被拒
        assertThat(bind(OUTSIDER, projectId, agentId, "APP-CORE-001").get("code").asInt()).isEqualTo(40301);
        assertThat(bind(DEVELOPER, projectId, agentId, "APP-CORE-001").get("code").asInt()).isEqualTo(40301);
        assertThat(bind(OPERATOR, projectId, agentId, "APP-CORE-001").get("code").asInt()).isEqualTo(40301);
        ObjectNode operate = objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId);
        assertThat(callApi("/v1/api/bindings/resync", OPERATOR, operate).get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/bindings/unbind", DEVELOPER, operate).get("code").asInt()).isEqualTo(40301);

        // read 权限放行 detail/list
        assertThat(callApi("/v1/api/bindings/detail", OPERATOR, operate).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/bindings/list", DEVELOPER,
                objectMapper.createObjectNode().put("projectId", projectId)).get("code").asInt()).isEqualTo(0);

        // 未认证 → 40101
        MvcResult unauthenticated = mockMvc.perform(post("/v1/api/bindings/list")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                objectMapper.createObjectNode().put("projectId", projectId))))
                .andReturn();
        assertThat(objectMapper.readTree(unauthenticated.getResponse().getContentAsString())
                .get("code").asInt()).isEqualTo(40101);
    }

    @Test
    void auditEventsRecorded() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String requestId = "req-ei-" + CODE_SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("appCode", "APP-RISK-003");
        mockMvc.perform(post("/v1/api/bindings/bind")
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getRequestId, requestId)
                .eq(AuditEvent::getModule, "enterprise-integration"));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getAction()).isEqualTo("binding.bind");
        assertThat(event.getResourceType()).isEqualTo("app_binding");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("\"appCode\":\"APP-RISK-003\"");
    }

    @Test
    void itsmChangeRecordAndList() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId).andGetId();
        String changeNo = "CHG" + CODE_SEQ.incrementAndGet();

        ObjectNode recordBody = objectMapper.createObjectNode()
                .put("projectId", projectId)
                .put("changeNo", changeNo)
                .put("agentId", agentId)
                .put("changeType", "PROD_RELEASE");
        recordBody.putObject("payload").put("version", "1.0.0").put("approver", "张核心");
        JsonNode record = callApi("/v1/api/itsm/changes/record", OWNER, recordBody);
        assertThat(record.get("code").asInt()).as(String.valueOf(record)).isEqualTo(0);
        assertThat(record.get("data").get("status").asText()).isEqualTo("RECORDED");
        assertThat(record.get("data").get("payload").asText()).contains("1.0.0");

        // changeNo 唯一 → 40901
        JsonNode dup = callApi("/v1/api/itsm/changes/record", OWNER, recordBody);
        assertThat(dup.get("code").asInt()).isEqualTo(40901);
        assertThat(dup.get("message").asText()).contains(changeNo);

        // 其他项目的 agent 不能登记到本项目 → 40301
        long otherProjectId = createProject(OWNER);
        long otherAgentId = createAgent(OWNER, otherProjectId).andGetId();
        JsonNode crossProject = callApi("/v1/api/itsm/changes/record", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("changeNo", "CHG" + CODE_SEQ.incrementAndGet())
                .put("agentId", otherAgentId).put("changeType", "PROD_RELEASE"));
        assertThat(crossProject.get("code").asInt()).isEqualTo(40301);

        JsonNode list = callApi("/v1/api/itsm/changes/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(list.get("data")).hasSize(1);
        assertThat(list.get("data").get(0).get("changeNo").asText()).isEqualTo(changeNo);
        JsonNode empty = callApi("/v1/api/itsm/changes/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", otherAgentId));
        assertThat(empty.get("code").asInt()).isEqualTo(0);
        assertThat(empty.get("data")).isEmpty();
    }

    // ---------- helpers ----------

    /** 注册 mock CMDB 应用（唯一编码，测试间隔离），返回 appCode */
    private String registerMockApp(String appName, String owner, CmdbAppStatus status) {
        String appCode = "APP-T-" + CODE_SEQ.incrementAndGet();
        mockCmdbConnector.putApp(new CmdbApp(appCode, appName, owner, "测试域", "B", status));
        return appCode;
    }

    /** 直接把 synced_at 改成 hoursAgo 小时前（构造 STALE 场景） */
    private void backdateSyncedAt(long agentId, long hoursAgo) {
        AppBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        binding.setSyncedAt(LocalDateTime.now().minusHours(hoursAgo));
        bindingMapper.updateById(binding);
    }

    private JsonNode bind(String operator, long projectId, long agentId, String appCode) throws Exception {
        return callApi("/v1/api/bindings/bind", operator, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("appCode", appCode));
    }

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "ei-p" + CODE_SEQ.incrementAndGet())
                .put("name", "企业集成测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private CheckedResponse createAgent(String operator, long projectId) {
        return new CheckedResponse("/v1/api/agents/create", operator, objectMapper.createObjectNode()
                .put("code", "ei-ag-" + CODE_SEQ.incrementAndGet())
                .put("name", "企业集成测试 Agent")
                .put("projectId", projectId));
    }

    private CheckedResponse addMember(String operator, long projectId, String subjectId, String role) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("subjectType", "USER")
                .put("subjectId", subjectId)
                .put("role", role);
        return new CheckedResponse("/v1/api/projects/" + projectId + "/members/add", operator, body);
    }

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + userId);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private class CheckedResponse {
        private final String url;
        private final String userId;
        private final ObjectNode body;

        CheckedResponse(String url, String userId, ObjectNode body) {
            this.url = url;
            this.userId = userId;
            this.body = body;
        }

        JsonNode andExpectCode(int expectedCode) throws Exception {
            JsonNode response = callApi(url, userId, body);
            assertThat(response.get("code").asInt())
                    .as("POST %s as %s => %s", url, userId, response)
                    .isEqualTo(expectedCode);
            return response;
        }

        long andGetId() throws Exception {
            JsonNode response = andExpectCode(0);
            return response.get("data").get("id").asLong();
        }
    }
}
