package com.bosc.agentops.assethub;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.mapper.AssetVersionMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 资产文件上传端到端测试：新建资产+DRAFT 版本 / 同 code 加版本 / 重复版本 40901 /
 * 坏扩展名与超限 40001 / 文件落盘与 sha256 / 审计落库。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssetUploadIntegrationTest {

    private static final String OWNER = "u1001";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);
    private static final Path UPLOAD_DIR = createTempDir();

    @DynamicPropertySource
    static void uploadDirProperty(DynamicPropertyRegistry registry) {
        registry.add("agentops.asset-upload.dir", () -> UPLOAD_DIR.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetVersionMapper assetVersionMapper;
    @Autowired
    private AssetAccessService assetAccessService;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void uploadCreatesAssetAndDraftVersion() throws Exception {
        long projectId = createProject();
        String code = "up-skill-" + CODE_SEQ.incrementAndGet();
        byte[] content = "# 总结技能\n对输入文本做摘要".getBytes(StandardCharsets.UTF_8);

        JsonNode resp = upload(OWNER, projectId, "skill.md", "text/markdown", content,
                "SKILL", "总结技能", code, null);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        JsonNode data = resp.get("data");
        long assetId = data.get("assetId").asLong();
        assertThat(data.get("assetCode").asText()).isEqualTo(code);
        assertThat(data.get("assetStatus").asText()).isEqualTo("DRAFT");
        assertThat(data.get("version").asText()).isEqualTo("1.0.0");
        assertThat(data.get("versionStatus").asText()).isEqualTo("DRAFT");
        assertThat(data.get("filename").asText()).isEqualTo("skill.md");
        assertThat(data.get("size").asLong()).isEqualTo(content.length);
        assertThat(data.get("sha256").asText()).isEqualTo(sha256(content));

        // 文件落盘 {assetId}/{version}/{filename}
        Path stored = UPLOAD_DIR.resolve(String.valueOf(assetId)).resolve("1.0.0").resolve("skill.md");
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);

        // definition JSON 记录文件摘要
        AssetVersion version = assetVersionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId).eq(AssetVersion::getVersion, "1.0.0"));
        JsonNode definition = objectMapper.readTree(version.getDefinition());
        assertThat(definition.get("filename").asText()).isEqualTo("skill.md");
        assertThat(definition.get("size").asLong()).isEqualTo(content.length);
        assertThat(definition.get("sha256").asText()).isEqualTo(sha256(content));
        assertThat(definition.get("storedPath").asText()).endsWith("skill.md");
        assertThat(definition.get("contentType").asText()).isEqualTo("text/markdown");

        // 草稿不可用于引用（发布走既有 publish）
        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isFalse();
    }

    @Test
    void uploadSameCodeAddsDraftVersion() throws Exception {
        long projectId = createProject();
        String code = "up-ver-" + CODE_SEQ.incrementAndGet();

        JsonNode first = upload(OWNER, projectId, "a.md", "text/markdown", "v1".getBytes(),
                "SKILL", "版本技能", code, "1.0.0");
        assertThat(first.get("code").asInt()).isEqualTo(0);
        long assetId = first.get("data").get("assetId").asLong();

        JsonNode second = upload(OWNER, projectId, "b.md", "text/markdown", "v2".getBytes(),
                "SKILL", "版本技能", code, "1.1.0");
        assertThat(second.get("code").asInt()).as(String.valueOf(second)).isEqualTo(0);
        assertThat(second.get("data").get("assetId").asLong()).isEqualTo(assetId);
        assertThat(second.get("data").get("versionStatus").asText()).isEqualTo("DRAFT");

        List<AssetVersion> versions = assetVersionMapper.selectList(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId));
        assertThat(versions).hasSize(2);
        assertThat(versions).allMatch(v -> v.getStatus().name().equals("DRAFT"));
    }

    @Test
    void uploadDuplicateVersionRejected() throws Exception {
        long projectId = createProject();
        String code = "up-dup-" + CODE_SEQ.incrementAndGet();
        upload(OWNER, projectId, "a.md", "text/markdown", "v1".getBytes(),
                "SKILL", "重复版本", code, "1.0.0");

        JsonNode dup = upload(OWNER, projectId, "a.md", "text/markdown", "v1-again".getBytes(),
                "SKILL", "重复版本", code, "1.0.0");
        assertThat(dup.get("code").asInt()).isEqualTo(40901);
    }

    @Test
    void uploadRejectsBadExtensionAndOversize() throws Exception {
        long projectId = createProject();
        JsonNode badExt = upload(OWNER, projectId, "evil.exe", "application/octet-stream",
                "x".getBytes(), "SKILL", "坏扩展名", "up-ext-" + CODE_SEQ.incrementAndGet(), null);
        assertThat(badExt.get("code").asInt()).isEqualTo(40001);

        byte[] oversize = new byte[20 * 1024 * 1024 + 1];
        JsonNode tooBig = upload(OWNER, projectId, "big.zip", "application/zip", oversize,
                "SKILL", "超大文件", "up-big-" + CODE_SEQ.incrementAndGet(), null);
        assertThat(tooBig.get("code").asInt()).isEqualTo(40001);
    }

    @Test
    void uploadRecordsAuditEvent() throws Exception {
        long projectId = createProject();
        String requestId = "req-upload-" + CODE_SEQ.incrementAndGet();
        MockMultipartFile file = new MockMultipartFile("file", "tool.json", "application/json",
                "{\"name\":\"t\"}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/v1/api/assets/upload").file(file)
                        .param("projectId", String.valueOf(projectId))
                        .param("assetType", "MCP_TOOL")
                        .param("name", "审计上传")
                        .param("code", "up-audit-" + CODE_SEQ.incrementAndGet())
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getRequestId, requestId));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getModule()).isEqualTo("asset-hub");
        assertThat(event.getAction()).isEqualTo("asset.upload");
        assertThat(event.getResourceType()).isEqualTo("asset");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("tool.json");
    }

    @Test
    void uploadThenPublishDraftThenUsable() throws Exception {
        long projectId = createProject();
        String code = "up-pd-" + CODE_SEQ.incrementAndGet();
        JsonNode uploadResp = upload(OWNER, projectId, "skill.md", "text/markdown",
                "prompt v1".getBytes(), "SKILL", "草稿发布技能", code, "1.0.0");
        assertThat(uploadResp.get("code").asInt()).isEqualTo(0);
        long assetId = uploadResp.get("data").get("assetId").asLong();

        // versions/list 能看到 DRAFT 及其状态
        JsonNode listResp = callApi("/v1/api/assets/" + assetId + "/versions/list", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(listResp.get("code").asInt()).isEqualTo(0);
        assertThat(listResp.get("data")).hasSize(1);
        assertThat(listResp.get("data").get(0).get("status").asText()).isEqualTo("DRAFT");
        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isFalse();

        // publish-draft：版本 PUBLISHED + 资产联动 PUBLISHED + 审计
        String requestId = "req-pd-" + CODE_SEQ.incrementAndGet();
        JsonNode publishResp = publishDraft(OWNER, assetId, projectId, "1.0.0", requestId);
        assertThat(publishResp.get("code").asInt()).as(String.valueOf(publishResp)).isEqualTo(0);
        assertThat(publishResp.get("data").get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishResp.get("data").get("publishedBy").asText()).isEqualTo(OWNER);
        assertThat(publishResp.get("data").get("publishedAt").isTextual()).isTrue();
        assertThat(assetAccessService.checkUsable(assetId, "1.0.0", projectId)).isTrue();
        JsonNode detailResp = callApi("/v1/api/assets/detail", OWNER,
                objectMapper.createObjectNode().put("id", assetId).put("projectId", projectId));
        assertThat(detailResp.get("data").get("status").asText()).isEqualTo("PUBLISHED");

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getRequestId, requestId));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getModule()).isEqualTo("asset-hub");
        assertThat(events.get(0).getAction()).isEqualTo("version.publish-draft");
        assertThat(events.get(0).getResult()).isEqualTo("SUCCESS");

        // 资产已 PUBLISHED 后仍可发布新草稿版本
        upload(OWNER, projectId, "skill-v2.md", "text/markdown", "prompt v2".getBytes(),
                "SKILL", "草稿发布技能", code, "1.1.0");
        JsonNode publishV2 = publishDraft(OWNER, assetId, projectId, "1.1.0", null);
        assertThat(publishV2.get("code").asInt()).as(String.valueOf(publishV2)).isEqualTo(0);
        assertThat(assetAccessService.checkUsable(assetId, "1.1.0", projectId)).isTrue();
    }

    @Test
    void publishDraftDuplicateRejected() throws Exception {
        long projectId = createProject();
        String code = "up-pdd-" + CODE_SEQ.incrementAndGet();
        JsonNode uploadResp = upload(OWNER, projectId, "a.md", "text/markdown", "v1".getBytes(),
                "SKILL", "重复发布", code, "1.0.0");
        long assetId = uploadResp.get("data").get("assetId").asLong();

        assertThat(publishDraft(OWNER, assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(0);
        // 重复发布同一版本 → 40902
        assertThat(publishDraft(OWNER, assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(40902);
        // 版本不存在 → 40401
        assertThat(publishDraft(OWNER, assetId, projectId, "9.9.9", null).get("code").asInt()).isEqualTo(40401);
    }

    @Test
    void publishDraftRejectedForOfflineAssetAndVersion() throws Exception {
        long projectId = createProject();
        String code = "up-pdo-" + CODE_SEQ.incrementAndGet();
        JsonNode uploadResp = upload(OWNER, projectId, "a.md", "text/markdown", "v1".getBytes(),
                "SKILL", "下线拒绝", code, "1.0.0");
        long assetId = uploadResp.get("data").get("assetId").asLong();

        // OFFLINE 版本（直接落库构造，平台暂无版本下线接口）→ 40902
        AssetVersion offlineVersion = new AssetVersion();
        offlineVersion.setAssetId(assetId);
        offlineVersion.setVersion("0.9.0");
        offlineVersion.setStatus(com.bosc.agentops.assethub.entity.AssetVersionStatus.OFFLINE);
        offlineVersion.setCreatedBy(OWNER);
        assetVersionMapper.insert(offlineVersion);
        assertThat(publishDraft(OWNER, assetId, projectId, "0.9.0", null).get("code").asInt()).isEqualTo(40902);

        // 资产整体 OFFLINE → 40902
        JsonNode offlineResp = callApi("/v1/api/assets/offline", OWNER,
                objectMapper.createObjectNode().put("id", assetId).put("projectId", projectId));
        assertThat(offlineResp.get("code").asInt()).isEqualTo(0);
        assertThat(publishDraft(OWNER, assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(40902);
    }

    @Test
    void publishDraftRequiresPublishPermission() throws Exception {
        long projectId = createProject();
        String operator = "u1004";
        JsonNode addResp = callApi("/v1/api/projects/" + projectId + "/members/add", OWNER,
                objectMapper.createObjectNode()
                        .put("subjectType", "USER").put("subjectId", operator).put("role", "OPERATOR"));
        assertThat(addResp.get("code").asInt()).isEqualTo(0);

        String code = "up-pdp-" + CODE_SEQ.incrementAndGet();
        JsonNode uploadResp = upload(OWNER, projectId, "a.md", "text/markdown", "v1".getBytes(),
                "SKILL", "权限校验", code, "1.0.0");
        long assetId = uploadResp.get("data").get("assetId").asLong();

        // OPERATOR 无 asset:publish → 40301；非成员 → 40301
        assertThat(publishDraft(operator, assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(40301);
        assertThat(publishDraft("u1005", assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void publishDraftMcpToolRequiresParent() throws Exception {
        long projectId = createProject();
        String code = "up-pdm-" + CODE_SEQ.incrementAndGet();
        JsonNode uploadResp = upload(OWNER, projectId, "tool.json", "application/json",
                "{\"name\":\"t\"}".getBytes(), "MCP_TOOL", "孤儿工具", code, "1.0.0");
        assertThat(uploadResp.get("code").asInt()).isEqualTo(0);
        long assetId = uploadResp.get("data").get("assetId").asLong();

        assertThat(publishDraft(OWNER, assetId, projectId, "1.0.0", null).get("code").asInt()).isEqualTo(40902);
    }

    // ---------- helpers ----------

    private JsonNode publishDraft(String operator, long assetId, long projectId, String version,
                                  String requestId) throws Exception {
        return callApi("/v1/api/assets/versions/publish-draft", operator,
                objectMapper.createObjectNode()
                        .put("assetId", assetId).put("projectId", projectId).put("version", version),
                requestId);
    }

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        return callApi(url, userId, body, null);
    }

    private JsonNode callApi(String url, String userId, ObjectNode body, String requestId) throws Exception {
        var request = post(url).header("Authorization", "Bearer " + userId);
        if (requestId != null) {
            request.header("X-Request-Id", requestId);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode upload(String operator, long projectId, String filename, String contentType,
                            byte[] content, String assetType, String name, String code, String version)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        var request = multipart("/v1/api/assets/upload").file(file)
                .param("projectId", String.valueOf(projectId))
                .param("assetType", assetType)
                .param("name", name)
                .header("Authorization", "Bearer " + operator);
        if (code != null) {
            request.param("code", code);
        }
        if (version != null) {
            request.param("version", version);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long createProject() throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "up-p" + CODE_SEQ.incrementAndGet())
                .put("name", "上传测试项目");
        MvcResult result = mockMvc.perform(post("/v1/api/projects/create")
                        .header("Authorization", "Bearer " + OWNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("agentops-upload-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
