package com.bosc.agentops.modelknowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.modelknowledge.dto.EffectiveModel;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.entity.ProjectModelGrant;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseRefMapper;
import com.bosc.agentops.modelknowledge.mapper.ProjectModelGrantMapper;
import com.bosc.agentops.modelknowledge.service.KnowledgeAccessService;
import com.bosc.agentops.modelknowledge.service.ModelAccessService;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 模块 03 端到端集成测试：目录管理 + 项目授权 + 参数合并 + SPI 判定 + 权限拦截 + 审计落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModelKnowledgeIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String DEVELOPER = "u1003";
    private static final String OUTSIDER = "u1004";
    private static final String OPERATOR = "u1006";
    private static final long AGENT_ID = 9001L;

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ModelAccessService modelAccessService;
    @Autowired
    private KnowledgeAccessService knowledgeAccessService;
    @Autowired
    private ProjectModelGrantMapper projectModelGrantMapper;
    @Autowired
    private KnowledgeBaseRefMapper knowledgeBaseRefMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void grantMakesModelEffectiveWithParamMerge() throws Exception {
        long providerId = createProvider(OWNER);
        // 默认参数：temperature/topP/maxTokens
        long modelServiceId = createModelService(OWNER, providerId, "qwen-max-" + CODE_SEQ.incrementAndGet(),
                "{\"chat\":true}",
                "{\"temperature\":0.7,\"topP\":0.9,\"maxTokens\":2048}");
        long projectId = createProject(OWNER);

        // 授权 + 参数策略：temperature 覆盖为 0.2；maxTokens 覆盖 8192 但上限 4096 → 收敛；topP 不在 policy → 剔除
        JsonNode grantResp = callApi("/v1/api/projects/" + projectId + "/model-grants/grant", OWNER,
                objectMapper.createObjectNode()
                        .put("modelServiceId", modelServiceId)
                        .set("paramPolicy", objectMapper.readTree(
                                "{\"temperature\":{\"value\":0.2},\"maxTokens\":{\"value\":8192,\"max\":4096}}")));
        assertThat(grantResp.get("code").asInt()).isEqualTo(0);

        // REST：effective-models 返回合并结果
        JsonNode listResp = callApi("/v1/api/projects/" + projectId + "/model-grants/effective-models", OWNER, null);
        assertThat(listResp.get("code").asInt()).isEqualTo(0);
        assertThat(listResp.get("data")).hasSize(1);
        JsonNode model = listResp.get("data").get(0);
        assertThat(model.get("modelServiceId").asLong()).isEqualTo(modelServiceId);
        assertThat(model.get("effectiveParams").get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(model.get("effectiveParams").get("maxTokens").asInt()).isEqualTo(4096);
        assertThat(model.get("effectiveParams").has("topP")).isFalse();
        assertThat(model.get("removedKeys")).hasSize(1);
        assertThat(model.get("removedKeys").get(0).asText()).isEqualTo("topP");

        // SPI：同样的合并结果 + checkModelAllowed
        List<EffectiveModel> effectiveModels = modelAccessService.effectiveModels(projectId);
        assertThat(effectiveModels).hasSize(1);
        assertThat(effectiveModels.get(0).getEffectiveParams())
                .containsEntry("maxTokens", 4096);
        assertThat(((Number) effectiveModels.get(0).getEffectiveParams().get("temperature")).doubleValue())
                .isEqualTo(0.2);
        assertThat(effectiveModels.get(0).getRemovedKeys()).containsExactly("topP");
        String modelCode = effectiveModels.get(0).getModelCode();
        assertThat(modelAccessService.checkModelAllowed(projectId, modelCode)).isTrue();
    }

    @Test
    void ungrantedProjectCannotUseModel() throws Exception {
        long providerId = createProvider(OWNER);
        long modelServiceId = createModelService(OWNER, providerId, "qwen-plus-" + CODE_SEQ.incrementAndGet(),
                null, "{\"temperature\":0.7}");
        long grantedProject = createProject(OWNER);
        long otherProject = createProject(OWNER);

        grantModel(OWNER, grantedProject, modelServiceId, null).andExpectCode(0);

        JsonNode listResp = callApi("/v1/api/projects/" + grantedProject + "/model-grants/effective-models",
                OWNER, null);
        String modelCode = listResp.get("data").get(0).get("modelCode").asText();
        // 未授权项目：SPI 判定拒绝，effectiveModels 为空
        assertThat(modelAccessService.checkModelAllowed(otherProject, modelCode)).isFalse();
        assertThat(modelAccessService.effectiveModels(otherProject)).isEmpty();
    }

    @Test
    void kbGrantRefBindThenRevoke() throws Exception {
        long kbId = createKnowledgeBase(OWNER);
        long projectId = createProject(OWNER);

        // 无授权时判定拒绝
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, null, kbId)).isFalse();

        grantKb(OWNER, projectId, kbId).andExpectCode(0);
        // 项目级 grant 存在 → 项目级判定放行
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, null, kbId)).isTrue();
        // 但 agent 级尚无 ref → 优先按 ref 判定，拒绝
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, AGENT_ID, kbId)).isFalse();

        JsonNode bindResp = callApi("/v1/api/projects/" + projectId + "/kb-refs/bind", OWNER,
                objectMapper.createObjectNode()
                        .put("kbId", kbId)
                        .put("agentId", AGENT_ID)
                        .put("refVersion", "v1"));
        assertThat(bindResp.get("code").asInt()).isEqualTo(0);
        long refId = bindResp.get("data").get("id").asLong();

        assertThat(knowledgeAccessService.checkKbAllowed(projectId, AGENT_ID, kbId)).isTrue();
        assertThat(knowledgeAccessService.listRefs(projectId, AGENT_ID)).hasSize(1);
        assertThat(knowledgeAccessService.listRefs(projectId, null)).hasSize(1);

        // revoke 授权后：项目级与 agent 级判定都拒绝（ref 记录仍在）
        revokeKb(OWNER, projectId, kbId).andExpectCode(0);
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, null, kbId)).isFalse();
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, AGENT_ID, kbId)).isFalse();

        // unbind 走权限面正常删除
        JsonNode unbindResp = callApi("/v1/api/projects/" + projectId + "/kb-refs/unbind", OWNER,
                objectMapper.createObjectNode().put("refId", refId));
        assertThat(unbindResp.get("code").asInt()).isEqualTo(0);
        assertThat(knowledgeBaseRefMapper.selectById(refId)).isNull();
    }

    @Test
    void grantEndpointsEnforceProjectPermission() throws Exception {
        long providerId = createProvider(OWNER);
        long modelServiceId = createModelService(OWNER, providerId, "glm-" + CODE_SEQ.incrementAndGet(),
                null, null);
        long kbId = createKnowledgeBase(OWNER);
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, DEVELOPER, "DEVELOPER").andExpectCode(0);
        addMember(OWNER, projectId, OPERATOR, "OPERATOR").andExpectCode(0);

        // 非成员：grant 被拒（fail-closed）
        grantModel(OUTSIDER, projectId, modelServiceId, null).andExpectCode(40301);
        grantKb(OUTSIDER, projectId, kbId).andExpectCode(40301);
        // OPERATOR 只有 read：grant 被拒
        grantModel(OPERATOR, projectId, modelServiceId, null).andExpectCode(40301);
        // DEVELOPER 有 grant：放行
        grantModel(DEVELOPER, projectId, modelServiceId, null).andExpectCode(0);
        grantKb(DEVELOPER, projectId, kbId).andExpectCode(0);
        // OPERATOR 可读列表
        JsonNode listResp = callApi("/v1/api/projects/" + projectId + "/model-grants/list", OPERATOR, null);
        assertThat(listResp.get("code").asInt()).isEqualTo(0);
        assertThat(listResp.get("data")).hasSize(1);
        JsonNode kbListResp = callApi("/v1/api/projects/" + projectId + "/kb-grants/list", OPERATOR, null);
        assertThat(kbListResp.get("code").asInt()).isEqualTo(0);
        assertThat(kbListResp.get("data")).hasSize(1);
    }

    @Test
    void disableHidesEffectiveModelsAndRefsButKeepsRecords() throws Exception {
        long providerId = createProvider(OWNER);
        long modelServiceId = createModelService(OWNER, providerId, "ds-" + CODE_SEQ.incrementAndGet(),
                null, "{\"temperature\":0.7}");
        long kbId = createKnowledgeBase(OWNER);
        long projectId = createProject(OWNER);
        grantModel(OWNER, projectId, modelServiceId, null).andExpectCode(0);
        grantKb(OWNER, projectId, kbId).andExpectCode(0);
        callApi("/v1/api/projects/" + projectId + "/kb-refs/bind", OWNER,
                objectMapper.createObjectNode().put("kbId", kbId).put("agentId", AGENT_ID));

        // 停用模型目录项：effectiveModels 不再返回，授权记录保留
        disableCatalogItem("/v1/api/model-services/disable", OWNER, modelServiceId).andExpectCode(0);
        assertThat(modelAccessService.effectiveModels(projectId)).isEmpty();
        List<ProjectModelGrant> grants = projectModelGrantMapper.selectList(
                new LambdaQueryWrapper<ProjectModelGrant>()
                        .eq(ProjectModelGrant::getProjectId, projectId)
                        .eq(ProjectModelGrant::getModelServiceId, modelServiceId));
        assertThat(grants).hasSize(1);

        // 停用知识库：listRefs 不再返回、判定拒绝，引用记录保留
        disableCatalogItem("/v1/api/knowledge-bases/disable", OWNER, kbId).andExpectCode(0);
        assertThat(knowledgeAccessService.listRefs(projectId, AGENT_ID)).isEmpty();
        assertThat(knowledgeAccessService.checkKbAllowed(projectId, AGENT_ID, kbId)).isFalse();
        List<KnowledgeBaseRef> refs = knowledgeBaseRefMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseRef>().eq(KnowledgeBaseRef::getProjectId, projectId));
        assertThat(refs).hasSize(1);

        // 重复停用 → 状态冲突
        disableCatalogItem("/v1/api/model-services/disable", OWNER, modelServiceId).andExpectCode(40902);
    }

    @Test
    void auditEventsRecorded() throws Exception {
        String requestId = "req-mk-" + CODE_SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "pv-audit-" + CODE_SEQ.incrementAndGet())
                .put("name", "审计 Provider")
                .put("providerType", "OPENAI_COMPAT")
                .put("authType", "GATEWAY_PASSTHROUGH");
        mockMvc.perform(post("/v1/api/model-providers/create")
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getRequestId, requestId));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getModule()).isEqualTo("model-knowledge");
        assertThat(event.getAction()).isEqualTo("provider.create");
        assertThat(event.getResourceType()).isEqualTo("model_provider");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("审计 Provider");
    }

    @Test
    void platformCatalogRequiresAuthenticationOnly() throws Exception {
        // 本期约定：平台级目录管理 = 认证用户 + 记录操作人（后续接平台运营角色收紧）
        // 无任何项目成员身份的认证用户也可建目录
        long providerId = createProvider(OUTSIDER);
        assertThat(providerId).isGreaterThan(0);

        // 未认证请求被拒
        MvcResult result = mockMvc.perform(post("/v1/api/model-providers/list")).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("code").asInt()).isEqualTo(40101);
    }

    // ---------- helpers ----------

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "mk-p" + CODE_SEQ.incrementAndGet())
                .put("name", "模型知识测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createProvider(String operator) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "pv-" + CODE_SEQ.incrementAndGet())
                .put("name", "行内模型网关")
                .put("providerType", "OPENAI_COMPAT")
                .put("endpoint", "https://llm-gw.example.com/v1")
                .put("authType", "CREDENTIAL_REF")
                .put("credentialRef", "kms://agentops/llm-gw");
        JsonNode response = callApi("/v1/api/model-providers/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createModelService(String operator, long providerId, String modelCode,
                                    String capabilities, String defaultParams) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("providerId", providerId)
                .put("modelCode", modelCode)
                .put("displayName", modelCode);
        if (capabilities != null) {
            body.set("capabilities", objectMapper.readTree(capabilities));
        }
        if (defaultParams != null) {
            body.set("defaultParams", objectMapper.readTree(defaultParams));
        }
        JsonNode response = callApi("/v1/api/model-services/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createKnowledgeBase(String operator) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "kb-" + CODE_SEQ.incrementAndGet())
                .put("name", "制度知识库")
                .put("kbType", "行内ES")
                .put("endpoint", "https://kb.example.com")
                .set("connectionConfig", objectMapper.readTree("{\"index\":\"policy\"}"));
        JsonNode response = callApi("/v1/api/knowledge-bases/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private CheckedResponse grantModel(String operator, long projectId, long modelServiceId, String paramPolicy)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("modelServiceId", modelServiceId);
        if (paramPolicy != null) {
            body.set("paramPolicy", objectMapper.readTree(paramPolicy));
        }
        return new CheckedResponse("/v1/api/projects/" + projectId + "/model-grants/grant", operator, body);
    }

    private CheckedResponse grantKb(String operator, long projectId, long kbId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("kbId", kbId)
                .set("scope", objectMapper.createObjectNode().putArray("collections").add("c1"));
        return new CheckedResponse("/v1/api/projects/" + projectId + "/kb-grants/grant", operator, body);
    }

    private CheckedResponse revokeKb(String operator, long projectId, long kbId) {
        return new CheckedResponse("/v1/api/projects/" + projectId + "/kb-grants/revoke", operator,
                objectMapper.createObjectNode().put("kbId", kbId));
    }

    private CheckedResponse addMember(String operator, long projectId, String subjectId, String role) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("subjectType", "USER")
                .put("subjectId", subjectId)
                .put("role", role);
        return new CheckedResponse("/v1/api/projects/" + projectId + "/members/add", operator, body);
    }

    private CheckedResponse disableCatalogItem(String url, String operator, long id) {
        return new CheckedResponse(url, operator, objectMapper.createObjectNode().put("id", id));
    }

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + userId);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

        void andExpectCode(int expectedCode) throws Exception {
            JsonNode response = callApi(url, userId, body);
            assertThat(response.get("code").asInt())
                    .as("POST %s as %s => %s", url, userId, response)
                    .isEqualTo(expectedCode);
        }
    }
}
