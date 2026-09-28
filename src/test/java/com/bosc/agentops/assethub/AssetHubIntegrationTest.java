package com.bosc.agentops.assethub;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.entity.AssetReference;
import com.bosc.agentops.assethub.service.AssetAccessService;
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
 * 模块 04 端到端集成测试：资产生命周期 + 可见性/授权判定 + 版本不可变 + 引用登记 + OpenAPI 转换 + 审计落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssetHubIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String ADMIN = "u1002";
    private static final String DEVELOPER = "u1003";
    private static final long AGENT_ID = 9101L;

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetAccessService assetAccessService;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void skillPublishThenUsableInOwnerProject() throws Exception {
        long projectId = createProject(OWNER);
        long assetId = createAsset(OWNER, projectId, "SKILL", "PROJECT");

        publishVersion(OWNER, assetId, projectId, "1.0.0", "{\"prompt\":\"总结输入文本\"}").andExpectCode(0);
        // 资产未发布前：版本已发布但资产 DRAFT → 不可用
        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isFalse();

        publishAsset(OWNER, assetId, projectId).andExpectCode(0);
        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isTrue();

        JsonNode refResp = callApi("/v1/api/assets/" + assetId + "/references/add", OWNER,
                objectMapper.createObjectNode()
                        .put("projectId", projectId)
                        .put("assetVersion", "1.0.0")
                        .put("agentId", AGENT_ID));
        assertThat(refResp.get("code").asInt()).isEqualTo(0);
        long refId = refResp.get("data").get("id").asLong();

        // 幂等：重复引用返回已有记录
        JsonNode refResp2 = callApi("/v1/api/assets/" + assetId + "/references/add", OWNER,
                objectMapper.createObjectNode()
                        .put("projectId", projectId)
                        .put("assetVersion", "1.0.0")
                        .put("agentId", AGENT_ID));
        assertThat(refResp2.get("code").asInt()).isEqualTo(0);
        assertThat(refResp2.get("data").get("id").asLong()).isEqualTo(refId);
        assertThat(assetAccessService.listReferences(assetId)).hasSize(1);
    }

    @Test
    void projectVisibilityNotUsableFromOtherProject() throws Exception {
        long projectA = createProject(OWNER);
        long projectB = createProject(OWNER);
        long assetId = createAsset(OWNER, projectA, "SKILL", "PROJECT");
        publishVersion(OWNER, assetId, projectA, "1.0.0", null).andExpectCode(0);
        publishAsset(OWNER, assetId, projectA).andExpectCode(0);

        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectB)).isFalse();
        // 其他项目登记引用被拒
        addReference(OWNER, assetId, projectB, "1.0.0").andExpectCode(40301);
        // 其他项目不可见
        JsonNode detailResp = callApi("/v1/api/assets/detail", OWNER,
                objectMapper.createObjectNode().put("id", assetId).put("projectId", projectB));
        assertThat(detailResp.get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void sharedGrantThenRevokeKeepsReferences() throws Exception {
        long projectA = createProject(OWNER);
        long projectB = createProject(ADMIN); // ADMIN 是项目 B 的 OWNER
        long assetId = createAsset(OWNER, projectA, "MCP_SERVICE", "SHARED");
        publishVersion(OWNER, assetId, projectA, "2.0.0", "{\"endpoint\":\"http://mcp.local\"}").andExpectCode(0);
        publishAsset(OWNER, assetId, projectA).andExpectCode(0);

        // 未授权前 B 不可用
        assertThat(assetAccessService.checkUsable(assetId, "2.0.0", projectB)).isFalse();

        grant(OWNER, assetId, projectA, projectB).andExpectCode(0);
        assertThat(assetAccessService.checkUsable(assetId, "2.0.0", projectB)).isTrue();

        // B 项目成员（ADMIN 用户）登记引用
        addReference(ADMIN, assetId, projectB, "2.0.0").andExpectCode(0);
        assertThat(assetAccessService.listReferences(assetId)).hasSize(1);

        // revoke 后不可用，但已有引用记录保留可查
        revoke(OWNER, assetId, projectA, projectB).andExpectCode(0);
        assertThat(assetAccessService.checkUsable(assetId, "2.0.0", projectB)).isFalse();
        assertThat(assetAccessService.listReferences(assetId)).hasSize(1);
        JsonNode refListResp = callApi("/v1/api/assets/" + assetId + "/references/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectA));
        assertThat(refListResp.get("code").asInt()).isEqualTo(0);
        assertThat(refListResp.get("data")).hasSize(1);
    }

    @Test
    void publishedVersionDefinitionIsImmutable() throws Exception {
        long projectId = createProject(OWNER);
        long assetId = createAsset(OWNER, projectId, "SKILL", "PROJECT");
        publishVersion(OWNER, assetId, projectId, "1.0.0", "{\"prompt\":\"v1\"}").andExpectCode(0);

        // 改 PUBLISHED 版本的 definition → 状态冲突
        JsonNode updateResp = callApi("/v1/api/assets/update", OWNER,
                objectMapper.createObjectNode()
                        .put("id", assetId)
                        .put("projectId", projectId)
                        .put("version", "1.0.0")
                        .set("definition", objectMapper.readTree("{\"prompt\":\"v1-modified\"}")));
        assertThat(updateResp.get("code").asInt()).isEqualTo(40902);

        // 同版本号重复发布 → 冲突
        publishVersion(OWNER, assetId, projectId, "1.0.0", "{\"prompt\":\"v1-again\"}").andExpectCode(40901);

        // 版本号格式校验：非 x.y.z → 40001
        publishVersion(OWNER, assetId, projectId, "1.0", null).andExpectCode(40001);

        // 发新版本是允许的
        publishVersion(OWNER, assetId, projectId, "1.1.0", "{\"prompt\":\"v2\"}").andExpectCode(0);
        JsonNode versionsResp = callApi("/v1/api/assets/" + assetId + "/versions/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(versionsResp.get("data")).hasSize(2);
    }

    @Test
    void offlineBlocksNewReferencesButKeepsExisting() throws Exception {
        long projectId = createProject(OWNER);
        long assetId = createAsset(OWNER, projectId, "SKILL", "PROJECT");
        publishVersion(OWNER, assetId, projectId, "1.0.0", null).andExpectCode(0);
        publishAsset(OWNER, assetId, projectId).andExpectCode(0);
        addReference(OWNER, assetId, projectId, "1.0.0").andExpectCode(0);

        offline(OWNER, assetId, projectId).andExpectCode(0);

        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isFalse();
        // OFFLINE 后禁止新引用（换一个 agentId 避免命中幂等）
        JsonNode refResp = callApi("/v1/api/assets/" + assetId + "/references/add", OWNER,
                objectMapper.createObjectNode()
                        .put("projectId", projectId)
                        .put("assetVersion", "1.0.0")
                        .put("agentId", AGENT_ID + 1));
        assertThat(refResp.get("code").asInt()).isEqualTo(40301);
        // 已有引用保留可查
        assertThat(assetAccessService.listReferences(assetId)).hasSize(1);
        // 重复下线 → 状态冲突
        offline(OWNER, assetId, projectId).andExpectCode(40902);
    }

    @Test
    void offlineGrantRequireOwnerOrAdmin() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, DEVELOPER, "DEVELOPER").andExpectCode(0);
        addMember(OWNER, projectId, ADMIN, "ADMIN").andExpectCode(0);
        // 资产责任人 = 创建者 OWNER(u1001)
        long assetId = createAsset(OWNER, projectId, "SKILL", "SHARED");
        publishVersion(OWNER, assetId, projectId, "1.0.0", null).andExpectCode(0);
        publishAsset(OWNER, assetId, projectId).andExpectCode(0);

        // DEVELOPER 非责任人：offline / grant 均被拒（无 asset:offline / asset:grant 权限点）
        offline(DEVELOPER, assetId, projectId).andExpectCode(40301);
        grant(DEVELOPER, assetId, projectId, projectId + 999).andExpectCode(40301);

        // 项目内 ADMIN 即使不是责任人也可 offline
        offline(ADMIN, assetId, projectId).andExpectCode(0);
    }

    @Test
    void openApiRegisterThenConvertToMcpTool() throws Exception {
        long projectId = createProject(OWNER);
        String openApiJson = "{"
                + "\"openapi\":\"3.0.1\",\"info\":{\"title\":\"pets\",\"version\":\"1.0\"},"
                + "\"paths\":{"
                + "\"/pets\":{\"get\":{\"operationId\":\"listPets\",\"summary\":\"列出宠物\","
                + "\"parameters\":[{\"name\":\"limit\",\"in\":\"query\",\"schema\":{\"type\":\"integer\"}}]}},"
                + "\"/pets/{id}\":{\"post\":{\"summary\":\"更新宠物\","
                + "\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\"}}}}}}"
                + "}}";
        ObjectNode registerBody = objectMapper.createObjectNode()
                .put("code", "httpapi-" + CODE_SEQ.incrementAndGet())
                .put("name", "宠物服务历史接口")
                .put("projectId", projectId)
                .put("openApiJson", openApiJson);
        JsonNode registerResp = callApi("/v1/api/http-apis/register", OWNER, registerBody);
        assertThat(registerResp.get("code").asInt()).as(String.valueOf(registerResp)).isEqualTo(0);
        long httpApiId = registerResp.get("data").get("id").asLong();
        assertThat(registerResp.get("data").get("assetType").asText()).isEqualTo("HTTP_API");
        assertThat(registerResp.get("data").get("status").asText()).isEqualTo("DRAFT");

        // 工具草案：operationId 命名 + method+path 回退命名；parameters 原样保留
        JsonNode toolsResp = callApi("/v1/api/http-apis/" + httpApiId + "/tools/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(toolsResp.get("code").asInt()).isEqualTo(0);
        assertThat(toolsResp.get("data")).hasSize(2);
        JsonNode listPets = findByName(toolsResp.get("data"), "listPets");
        assertThat(listPets.get("description").asText()).isEqualTo("列出宠物");
        assertThat(listPets.get("parameters")).hasSize(1);
        assertThat(listPets.get("parameters").get(0).get("name").asText()).isEqualTo("limit");
        JsonNode fallback = findByName(toolsResp.get("data"), "post_pets_id");
        assertThat(fallback.get("method").asText()).isEqualTo("POST");
        assertThat(fallback.get("requestBody").get("content").has("application/json")).isTrue();

        // convert 生成 MCP_TOOL 草稿，definition 内含 source_http_api_id 回链
        ObjectNode convertBody = objectMapper.createObjectNode().put("projectId", projectId);
        convertBody.putArray("operations").add("listPets");
        JsonNode convertResp = callApi("/v1/api/http-apis/" + httpApiId + "/convert", OWNER, convertBody);
        assertThat(convertResp.get("code").asInt()).as(String.valueOf(convertResp)).isEqualTo(0);
        assertThat(convertResp.get("data")).hasSize(1);
        JsonNode tool = convertResp.get("data").get(0);
        assertThat(tool.get("assetType").asText()).isEqualTo("MCP_TOOL");
        assertThat(tool.get("status").asText()).isEqualTo("DRAFT");

        JsonNode toolVersions = callApi("/v1/api/assets/" + tool.get("id").asLong() + "/versions/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(toolVersions.get("data")).hasSize(1);
        String definition = toolVersions.get("data").get(0).get("definition").asText();
        assertThat(definition).contains("\"source_http_api_id\":" + httpApiId);
        assertThat(definition).contains("\"name\":\"listPets\"");

        // 非法文档：非 3.x → 40001
        ObjectNode badBody = objectMapper.createObjectNode()
                .put("code", "httpapi-bad-" + CODE_SEQ.incrementAndGet())
                .put("name", "旧版 Swagger")
                .put("projectId", projectId)
                .put("openApiJson", "{\"swagger\":\"2.0\",\"paths\":{}}");
        JsonNode badResp = callApi("/v1/api/http-apis/register", OWNER, badBody);
        assertThat(badResp.get("code").asInt()).isEqualTo(40001);
    }

    @Test
    void auditEventsRecorded() throws Exception {
        long projectId = createProject(OWNER);
        String requestId = "req-ah-" + CODE_SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "skill-audit-" + CODE_SEQ.incrementAndGet())
                .put("name", "审计 Skill")
                .put("assetType", "SKILL")
                .put("projectId", projectId)
                .put("visibility", "PROJECT");
        mockMvc.perform(post("/v1/api/assets/create")
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getRequestId, requestId));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getModule()).isEqualTo("asset-hub");
        assertThat(event.getAction()).isEqualTo("asset.create");
        assertThat(event.getResourceType()).isEqualTo("asset");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("审计 Skill");
    }

    // ---------- helpers ----------

    private JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        throw new AssertionError("工具草案不存在: " + name + " in " + array);
    }

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "ah-p" + CODE_SEQ.incrementAndGet())
                .put("name", "资产测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createAsset(String operator, long projectId, String assetType, String visibility) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "asset-" + CODE_SEQ.incrementAndGet())
                .put("name", "测试资产")
                .put("assetType", assetType)
                .put("projectId", projectId)
                .put("visibility", visibility);
        JsonNode response = callApi("/v1/api/assets/create", operator, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private CheckedResponse publishVersion(String operator, long assetId, long projectId,
                                           String version, String definition) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId)
                .put("version", version);
        if (definition != null) {
            body.set("definition", objectMapper.readTree(definition));
        }
        return new CheckedResponse("/v1/api/assets/" + assetId + "/versions/publish", operator, body);
    }

    private CheckedResponse publishAsset(String operator, long assetId, long projectId) {
        return new CheckedResponse("/v1/api/assets/publish", operator,
                objectMapper.createObjectNode().put("id", assetId).put("projectId", projectId));
    }

    private CheckedResponse offline(String operator, long assetId, long projectId) {
        return new CheckedResponse("/v1/api/assets/offline", operator,
                objectMapper.createObjectNode().put("id", assetId).put("projectId", projectId));
    }

    private CheckedResponse grant(String operator, long assetId, long projectId, long toProjectId) {
        return new CheckedResponse("/v1/api/assets/" + assetId + "/grants/grant", operator,
                objectMapper.createObjectNode().put("projectId", projectId).put("toProjectId", toProjectId));
    }

    private CheckedResponse revoke(String operator, long assetId, long projectId, long toProjectId) {
        return new CheckedResponse("/v1/api/assets/" + assetId + "/grants/revoke", operator,
                objectMapper.createObjectNode().put("projectId", projectId).put("toProjectId", toProjectId));
    }

    private CheckedResponse addReference(String operator, long assetId, long projectId, String version) {
        return new CheckedResponse("/v1/api/assets/" + assetId + "/references/add", operator,
                objectMapper.createObjectNode().put("projectId", projectId).put("assetVersion", version));
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

        void andExpectCode(int expectedCode) throws Exception {
            JsonNode response = callApi(url, userId, body);
            assertThat(response.get("code").asInt())
                    .as("POST %s as %s => %s", url, userId, response)
                    .isEqualTo(expectedCode);
        }
    }
}
