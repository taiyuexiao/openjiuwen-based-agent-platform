package com.bosc.agentops.agentdev;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.mapper.AgentVersionMapper;
import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.assethub.entity.AssetReference;
import com.bosc.agentops.assethub.mapper.AssetReferenceMapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseRefMapper;
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
 * 模块 02 端到端集成测试：Agent 生命周期 + 三类接入边界 + 声明登记校验（模型/参数策略/资产/知识库）
 * + 版本不可变 + 引用登记 + 脚手架 + 权限拦截 + 审计落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentDevIntegrationTest {

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
    private AgentVersionMapper agentVersionMapper;
    @Autowired
    private AssetReferenceMapper assetReferenceMapper;
    @Autowired
    private KnowledgeBaseRefMapper knowledgeBaseRefMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void nativeRegisterSuccessWritesReferences() throws Exception {
        long projectId = createProject(OWNER);
        String modelCode = grantedModel(projectId, null);
        long skillId = publishedAsset(projectId, "SKILL");
        long mcpId = publishedAsset(projectId, "MCP_SERVICE");
        long kbId = grantedKbWithProjectRef(projectId);

        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", modelCode).putObject("params").put("temperature", 0.5);
        declaration.putObject("prompt").put("templateName", "客服助手");
        declaration.putArray("skills").addObject().put("assetId", skillId).put("version", "1.0.0");
        declaration.putArray("mcpTools").addObject().put("assetId", mcpId).put("version", "1.0.0");
        declaration.putArray("knowledgeBases").addObject().put("kbId", kbId);
        declaration.putObject("memory").put("type", "in_memory");

        JsonNode registerResp = callApi("/v1/api/agents/" + agentId + "/versions/register", OWNER,
                objectMapper.createObjectNode()
                        .put("projectId", projectId)
                        .put("version", "1.0.0")
                        .set("declaration", declaration));
        assertThat(registerResp.get("code").asInt()).as(String.valueOf(registerResp)).isEqualTo(0);
        assertThat(registerResp.get("data").get("status").asText()).isEqualTo("REGISTERED");
        assertThat(registerResp.get("data").get("capabilityDegraded").asBoolean()).isFalse();
        assertThat(registerResp.get("data").get("registeredBy").asText()).isEqualTo(OWNER);

        // 引用登记：2 条 AssetReference（skill + mcp）+ 1 条 agent 级 KnowledgeBaseRef
        List<AssetReference> references = assetReferenceMapper.selectList(
                new LambdaQueryWrapper<AssetReference>()
                        .eq(AssetReference::getProjectId, projectId)
                        .eq(AssetReference::getAgentId, agentId));
        assertThat(references).hasSize(2);
        List<KnowledgeBaseRef> kbRefs = knowledgeBaseRefMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseRef>()
                        .eq(KnowledgeBaseRef::getProjectId, projectId)
                        .eq(KnowledgeBaseRef::getAgentId, agentId));
        assertThat(kbRefs).hasSize(1);
        assertThat(kbRefs.get(0).getKbId()).isEqualTo(kbId);

        JsonNode listResp = callApi("/v1/api/agents/" + agentId + "/versions/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(listResp.get("data")).hasSize(1);
        JsonNode detailResp = callApi("/v1/api/agents/" + agentId + "/versions/detail", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "1.0.0"));
        assertThat(detailResp.get("data").get("declaration").asText()).contains(modelCode);
    }

    @Test
    void registerRejectsUnauthorizedModel() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", "never-granted-model");

        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40910);
        assertThat(resp.get("message").asText()).contains("模型未授权").contains("never-granted-model");
        assertThat(agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentId, agentId))).isEmpty();
    }

    @Test
    void registerRejectsParamOutOfPolicy() throws Exception {
        long projectId = createProject(OWNER);
        // 策略仅允许 temperature；声明的 topP 在合并中被剔除 → 拒绝
        String modelCode = grantedModel(projectId, "{\"temperature\":{\"value\":0.2}}");
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", modelCode)
                .putObject("params").put("temperature", 0.5).put("topP", 0.9);

        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40910);
        assertThat(resp.get("message").asText()).contains("参数策略").contains("topP");
    }

    @Test
    void registerRejectsUnusableAsset() throws Exception {
        long projectId = createProject(OWNER);
        grantedModel(projectId, null);
        // 未发布的资产不可用
        long draftAssetId = createAsset(OWNER, projectId, "SKILL");
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putArray("skills").addObject().put("assetId", draftAssetId).put("version", "1.0.0");

        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40910);
        assertThat(resp.get("message").asText()).contains("Skill").contains("不可用")
                .contains(String.valueOf(draftAssetId));
    }

    @Test
    void registerRejectsKbWithoutRef() throws Exception {
        long projectId = createProject(OWNER);
        // 有项目级授权但无任何 ref → agent 级判定拒绝
        long kbId = createKnowledgeBase(OWNER);
        grantKb(OWNER, projectId, kbId).andExpectCode(0);
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putArray("knowledgeBases").addObject().put("kbId", kbId);

        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40910);
        assertThat(resp.get("message").asText()).contains("知识库").contains(String.valueOf(kbId));
    }

    @Test
    void registerCollectsAllFailuresInOneResponse() throws Exception {
        long projectId = createProject(OWNER);
        long kbId = createKnowledgeBase(OWNER); // 未授权
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", "bad-model");
        declaration.putArray("skills").addObject().put("assetId", 999001).put("version", "1.0.0");
        declaration.putArray("mcpTools").addObject().put("assetId", 999002).put("version", "2.0.0");
        declaration.putArray("knowledgeBases").addObject().put("kbId", kbId);

        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40910);
        // 全部失败项一次性返回
        String message = resp.get("message").asText();
        assertThat(message).contains("bad-model");
        assertThat(message).contains("999001");
        assertThat(message).contains("999002");
        assertThat(message).contains(String.valueOf(kbId));
    }

    @Test
    void registeredVersionDeclarationIsImmutable() throws Exception {
        long projectId = createProject(OWNER);
        String modelCode = grantedModel(projectId, null);
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", modelCode);
        register(OWNER, agentId, projectId, "1.0.0", declaration);

        // 同版本号再次登记（企图改写 declaration）→ 拒绝
        ObjectNode modified = objectMapper.createObjectNode();
        modified.putObject("model").put("modelCode", modelCode).putObject("params").put("temperature", 0.1);
        JsonNode resp = register(OWNER, agentId, projectId, "1.0.0", modified);
        assertThat(resp.get("code").asInt()).isEqualTo(40901);
        assertThat(resp.get("message").asText()).contains("不可变");
        // 原 declaration 未被改写
        JsonNode detail = callApi("/v1/api/agents/" + agentId + "/versions/detail", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "1.0.0"));
        assertThat(detail.get("data").get("declaration").asText()).doesNotContain("temperature");

        // 版本号格式校验：非 x.y.z → 40001
        JsonNode badVersion = register(OWNER, agentId, projectId, "1.0", declaration);
        assertThat(badVersion.get("code").asInt()).isEqualTo(40001);

        // 新版本号允许登记
        JsonNode ok = register(OWNER, agentId, projectId, "1.1.0", declaration);
        assertThat(ok.get("code").asInt()).isEqualTo(0);
    }

    @Test
    void adaptedEmptyDeclarationRegistersWithDegradedCapability() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId, "ADAPTED").andGetId();

        JsonNode resp = callApi("/v1/api/agents/" + agentId + "/versions/register", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "0.9.0"));
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        assertThat(resp.get("data").get("capabilityDegraded").asBoolean()).isTrue();

        // detail 中体现能力降级标记
        JsonNode detail = callApi("/v1/api/agents/" + agentId + "/versions/detail", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "0.9.0"));
        assertThat(detail.get("data").get("capabilityDegraded").asBoolean()).isTrue();

        // ADAPTED 带合法声明登记：不降级
        String modelCode = grantedModel(projectId, null);
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", modelCode);
        JsonNode resp2 = register(OWNER, agentId, projectId, "1.0.0", declaration);
        assertThat(resp2.get("code").asInt()).isEqualTo(0);
        assertThat(resp2.get("data").get("capabilityDegraded").asBoolean()).isFalse();

        // NATIVE 空声明 → 拒绝
        long nativeAgent = createAgent(OWNER, projectId, "NATIVE").andGetId();
        JsonNode nativeResp = callApi("/v1/api/agents/" + nativeAgent + "/versions/register", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "1.0.0"));
        assertThat(nativeResp.get("code").asInt()).isEqualTo(40001);
    }

    @Test
    void hostedModeRequiresEndpointsAndRejectsRegister() throws Exception {
        long projectId = createProject(OWNER);
        // HOSTED 缺运行入口/健康检查 → 40001
        JsonNode bad = callApi("/v1/api/agents/create", OWNER, objectMapper.createObjectNode()
                .put("code", "ag-" + CODE_SEQ.incrementAndGet())
                .put("name", "托管 Agent")
                .put("projectId", projectId)
                .put("accessMode", "HOSTED"));
        assertThat(bad.get("code").asInt()).isEqualTo(40001);

        JsonNode create = callApi("/v1/api/agents/create", OWNER, objectMapper.createObjectNode()
                .put("code", "ag-" + CODE_SEQ.incrementAndGet())
                .put("name", "托管 Agent")
                .put("projectId", projectId)
                .put("accessMode", "HOSTED")
                .put("runtimeEndpoint", "https://legacy-agent.internal/invoke")
                .put("healthEndpoint", "https://legacy-agent.internal/health"));
        assertThat(create.get("code").asInt()).as(String.valueOf(create)).isEqualTo(0);
        long agentId = create.get("data").get("id").asLong();
        assertThat(create.get("data").get("runtimeEndpoint").asText())
                .isEqualTo("https://legacy-agent.internal/invoke");

        // detail 中标注托管边界
        JsonNode detail = callApi("/v1/api/agents/detail", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(detail.get("data").get("capabilityNote").asText()).contains("托管");

        // HOSTED 不允许登记版本
        JsonNode resp = callApi("/v1/api/agents/" + agentId + "/versions/register", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "1.0.0"));
        assertThat(resp.get("code").asInt()).isEqualTo(40902);
        assertThat(resp.get("message").asText()).contains("托管");
    }

    @Test
    void archivedAgentCannotRegisterNewVersion() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        String modelCode = grantedModel(projectId, null);
        ObjectNode declaration = objectMapper.createObjectNode();
        declaration.putObject("model").put("modelCode", modelCode);
        assertThat(register(OWNER, agentId, projectId, "1.0.0", declaration).get("code").asInt()).isEqualTo(0);

        callApi("/v1/api/agents/archive", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        JsonNode resp = register(OWNER, agentId, projectId, "2.0.0", declaration);
        assertThat(resp.get("code").asInt()).isEqualTo(40902);
        assertThat(resp.get("message").asText()).contains("归档");

        // 重复归档 → 状态冲突
        JsonNode again = callApi("/v1/api/agents/archive", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(again.get("code").asInt()).isEqualTo(40902);
    }

    @Test
    void scaffoldsListAndDetail() throws Exception {
        JsonNode listResp = callApi("/v1/api/scaffolds/list", OWNER, null);
        assertThat(listResp.get("code").asInt()).isEqualTo(0);
        assertThat(listResp.get("data")).hasSize(2);
        JsonNode first = listResp.get("data").get(0);
        assertThat(first.get("files").isNull()).isTrue(); // 列表不回文件内容

        JsonNode react = callApi("/v1/api/scaffolds/detail", OWNER,
                objectMapper.createObjectNode().put("code", "react-agent-basic"));
        assertThat(react.get("code").asInt()).isEqualTo(0);
        assertThat(react.get("data").get("language").asText()).isEqualTo("python");
        JsonNode reactFiles = objectMapper.readTree(react.get("data").get("files").asText());
        assertThat(reactFiles.has("main.py")).isTrue();
        assertThat(reactFiles.get("main.py").asText())
                .contains("ReActAgent").contains("{{MODEL_CODE}}").contains("{{PROJECT_CODE}}");

        JsonNode workflow = callApi("/v1/api/scaffolds/detail", OWNER,
                objectMapper.createObjectNode().put("code", "workflow-basic"));
        JsonNode workflowFiles = objectMapper.readTree(workflow.get("data").get("files").asText());
        assertThat(workflowFiles.get("main.py").asText()).contains("Workflow").contains("{{MODEL_CODE}}");

        // 不存在的模板 → 40401；未认证 → 40101
        JsonNode missing = callApi("/v1/api/scaffolds/detail", OWNER,
                objectMapper.createObjectNode().put("code", "not-exist"));
        assertThat(missing.get("code").asInt()).isEqualTo(40401);
        MvcResult unauthenticated = mockMvc.perform(post("/v1/api/scaffolds/list")).andReturn();
        assertThat(objectMapper.readTree(unauthenticated.getResponse().getContentAsString())
                .get("code").asInt()).isEqualTo(40101);
    }

    @Test
    void agentEndpointsEnforceProjectPermission() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, DEVELOPER, "DEVELOPER").andExpectCode(0);
        addMember(OWNER, projectId, OPERATOR, "OPERATOR").andExpectCode(0);

        // 非成员：create 被拒（fail-closed）
        createAgent(OUTSIDER, projectId, "NATIVE").andExpectCode(40301);
        // OPERATOR 只有 read：create/register 被拒
        createAgent(OPERATOR, projectId, "NATIVE").andExpectCode(40301);
        JsonNode listResp = callApi("/v1/api/agents/list", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(listResp.get("code").asInt()).isEqualTo(0);

        // DEVELOPER：create + register 放行，archive 被拒
        long agentId = createAgent(DEVELOPER, projectId, "ADAPTED").andGetId();
        JsonNode registerResp = callApi("/v1/api/agents/" + agentId + "/versions/register", DEVELOPER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", "1.0.0"));
        assertThat(registerResp.get("code").asInt()).isEqualTo(0);
        JsonNode archiveResp = callApi("/v1/api/agents/archive", DEVELOPER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(archiveResp.get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void auditEventsRecorded() throws Exception {
        long projectId = createProject(OWNER);
        String modelCode = grantedModel(projectId, null);
        long agentId = createAgent(OWNER, projectId, "NATIVE").andGetId();
        String requestId = "req-ad-" + CODE_SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId)
                .put("version", "1.0.0");
        body.putObject("declaration").putObject("model").put("modelCode", modelCode);
        mockMvc.perform(post("/v1/api/agents/" + agentId + "/versions/register")
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>()
                        .eq(AuditEvent::getRequestId, requestId)
                        .eq(AuditEvent::getModule, "agent-dev"));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getAction()).isEqualTo("agent.version.register");
        assertThat(event.getResourceType()).isEqualTo("agent_version");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("\"version\":\"1.0.0\"");
    }

    // ---------- helpers ----------

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "ad-p" + CODE_SEQ.incrementAndGet())
                .put("name", "Agent 研发测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    /** 建模型目录（Provider + ModelService）并授权给项目，返回 modelCode */
    private String grantedModel(long projectId, String paramPolicy) throws Exception {
        String modelCode = "qwen-ad-" + CODE_SEQ.incrementAndGet();
        ObjectNode providerBody = objectMapper.createObjectNode()
                .put("code", "pv-ad-" + CODE_SEQ.incrementAndGet())
                .put("name", "行内模型网关")
                .put("providerType", "OPENAI_COMPAT")
                .put("endpoint", "https://llm-gw.example.com/v1")
                .put("authType", "CREDENTIAL_REF")
                .put("credentialRef", "kms://agentops/llm-gw");
        JsonNode providerResp = callApi("/v1/api/model-providers/create", OWNER, providerBody);
        long providerId = providerResp.get("data").get("id").asLong();
        ObjectNode serviceBody = objectMapper.createObjectNode()
                .put("providerId", providerId)
                .put("modelCode", modelCode)
                .put("displayName", modelCode)
                .set("defaultParams", objectMapper.readTree("{\"temperature\":0.7,\"topP\":0.9}"));
        JsonNode serviceResp = callApi("/v1/api/model-services/create", OWNER, serviceBody);
        long modelServiceId = serviceResp.get("data").get("id").asLong();
        grantModel(OWNER, projectId, modelServiceId, paramPolicy).andExpectCode(0);
        return modelCode;
    }

    /** 建知识库 + 项目授权 + 项目级 ref（agentId 为空），返回 kbId */
    private long grantedKbWithProjectRef(long projectId) throws Exception {
        long kbId = createKnowledgeBase(OWNER);
        grantKb(OWNER, projectId, kbId).andExpectCode(0);
        JsonNode bindResp = callApi("/v1/api/projects/" + projectId + "/kb-refs/bind", OWNER,
                objectMapper.createObjectNode().put("kbId", kbId));
        assertThat(bindResp.get("code").asInt()).as(String.valueOf(bindResp)).isEqualTo(0);
        return kbId;
    }

    private long createKnowledgeBase(String operator) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "kb-ad-" + CODE_SEQ.incrementAndGet())
                .put("name", "制度知识库")
                .put("kbType", "行内ES")
                .put("endpoint", "https://kb.example.com")
                .set("connectionConfig", objectMapper.readTree("{\"index\":\"policy\"}"));
        JsonNode response = callApi("/v1/api/knowledge-bases/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createAsset(String operator, long projectId, String assetType) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "asset-ad-" + CODE_SEQ.incrementAndGet())
                .put("name", "测试资产")
                .put("assetType", assetType)
                .put("projectId", projectId)
                .put("visibility", "PROJECT");
        JsonNode response = callApi("/v1/api/assets/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    /** 建资产 + 发布 1.0.0 版本 + 发布资产，返回 assetId */
    private long publishedAsset(long projectId, String assetType) throws Exception {
        long assetId = createAsset(OWNER, projectId, assetType);
        callApi("/v1/api/assets/" + assetId + "/versions/publish", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("version", "1.0.0"));
        JsonNode publishResp = callApi("/v1/api/assets/publish", OWNER, objectMapper.createObjectNode()
                .put("id", assetId).put("projectId", projectId));
        assertThat(publishResp.get("code").asInt()).as(String.valueOf(publishResp)).isEqualTo(0);
        return assetId;
    }

    private CheckedResponse createAgent(String operator, long projectId, String accessMode) {
        return new CheckedResponse("/v1/api/agents/create", operator, objectMapper.createObjectNode()
                .put("code", "ag-" + CODE_SEQ.incrementAndGet())
                .put("name", "测试 Agent")
                .put("projectId", projectId)
                .put("accessMode", accessMode));
    }

    /** register 并断言 code=0，返回响应 */
    private JsonNode register(String operator, long agentId, long projectId,
                              String version, ObjectNode declaration) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId)
                .put("version", version)
                .set("declaration", declaration);
        return callApi("/v1/api/agents/" + agentId + "/versions/register", operator, body);
    }

    private CheckedResponse grantModel(String operator, long projectId, long modelServiceId,
                                       String paramPolicy) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("modelServiceId", modelServiceId);
        if (paramPolicy != null) {
            body.set("paramPolicy", objectMapper.readTree(paramPolicy));
        }
        return new CheckedResponse("/v1/api/projects/" + projectId + "/model-grants/grant", operator, body);
    }

    private CheckedResponse grantKb(String operator, long projectId, long kbId) {
        return new CheckedResponse("/v1/api/projects/" + projectId + "/kb-grants/grant", operator,
                objectMapper.createObjectNode().put("kbId", kbId));
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

        CheckedResponse andExpectCode(int expectedCode) throws Exception {
            JsonNode response = callApi(url, userId, body);
            assertThat(response.get("code").asInt())
                    .as("POST %s as %s => %s", url, userId, response)
                    .isEqualTo(expectedCode);
            return this;
        }

        long andGetId() throws Exception {
            JsonNode response = callApi(url, userId, body);
            assertThat(response.get("code").asInt())
                    .as("POST %s as %s => %s", url, userId, response)
                    .isEqualTo(0);
            return response.get("data").get("id").asLong();
        }
    }
}
