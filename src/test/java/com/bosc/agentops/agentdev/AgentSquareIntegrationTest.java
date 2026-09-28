package com.bosc.agentops.agentdev;

import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.agentdev.entity.AgentVersionStatus;
import com.bosc.agentops.agentdev.mapper.AgentVersionMapper;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Agent 广场端到端测试：publish 后出现在 square/list（含项目名与版本摘要）、
 * unpublish 后消失、PROJECT 可见性不可见、关键词过滤、跨项目操作被拒。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentSquareIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String OUTSIDER = "u1006";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AgentVersionMapper agentVersionMapper;

    @Test
    void publishThenVisibleInSquareThenUnpublishDisappears() throws Exception {
        long projectId = createProject(OWNER, "广场源项目");
        long agentId = createAgent(OWNER, projectId, "sq-agent-");
        insertVersion(agentId, "1.0.0");
        insertVersion(agentId, "1.2.0");

        // publish 前不可见
        assertThat(findInSquare(agentId, null)).isNull();

        JsonNode publishResp = callApi("/v1/api/agents/publish", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(publishResp.get("code").asInt()).as(String.valueOf(publishResp)).isEqualTo(0);
        assertThat(publishResp.get("data").get("visibility").asText()).isEqualTo("PUBLIC");

        // 非项目成员的认证用户也能在广场看到（平台级接口）
        JsonNode item = findInSquareAs(agentId, null, OUTSIDER);
        assertThat(item).isNotNull();
        assertThat(item.get("projectId").asLong()).isEqualTo(projectId);
        assertThat(item.get("projectName").asText()).isEqualTo("广场源项目");
        assertThat(item.get("accessMode").asText()).isEqualTo("NATIVE");
        assertThat(item.get("latestVersion").asText()).isEqualTo("1.2.0");
        assertThat(item.get("versionCount").asLong()).isEqualTo(2);
        assertThat(item.get("createdAt").isTextual()).isTrue();

        // unpublish 后消失
        JsonNode unpublishResp = callApi("/v1/api/agents/unpublish", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(unpublishResp.get("code").asInt()).isEqualTo(0);
        assertThat(unpublishResp.get("data").get("visibility").asText()).isEqualTo("PROJECT");
        assertThat(findInSquare(agentId, null)).isNull();
    }

    @Test
    void keywordFiltersSquareList() throws Exception {
        long projectId = createProject(OWNER, "关键词项目");
        long hitAgent = createAgent(OWNER, projectId, "sq-kw-hit-");
        long missAgent = createAgent(OWNER, projectId, "sq-kw-miss-");
        publish(OWNER, hitAgent, projectId);
        publish(OWNER, missAgent, projectId);

        JsonNode hit = findInSquare(hitAgent, "sq-kw-hit");
        assertThat(hit).isNotNull();
        assertThat(findInSquare(missAgent, "sq-kw-hit")).isNull();
    }

    @Test
    void archivedAgentDisappearsFromSquare() throws Exception {
        long projectId = createProject(OWNER, "归档项目");
        long agentId = createAgent(OWNER, projectId, "sq-arch-");
        publish(OWNER, agentId, projectId);
        assertThat(findInSquare(agentId, null)).isNotNull();

        JsonNode archiveResp = callApi("/v1/api/agents/archive", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(archiveResp.get("code").asInt()).isEqualTo(0);
        assertThat(findInSquare(agentId, null)).isNull();
    }

    @Test
    void publishRequiresOwnerProjectAndMembership() throws Exception {
        long projectA = createProject(OWNER, "归属项目A");
        long projectB = createProject(OWNER, "归属项目B");
        long agentId = createAgent(OWNER, projectA, "sq-scope-");

        // 跨项目冒用：projectId 不匹配 → 40301
        JsonNode crossProject = callApi("/v1/api/agents/publish", OWNER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectB));
        assertThat(crossProject.get("code").asInt()).isEqualTo(40301);

        // 非项目成员 → 40301
        JsonNode outsider = callApi("/v1/api/agents/publish", OUTSIDER,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectA));
        assertThat(outsider.get("code").asInt()).isEqualTo(40301);
    }

    // ---------- helpers ----------

    private JsonNode findInSquare(long agentId, String keyword) throws Exception {
        return findInSquareAs(agentId, keyword, OWNER);
    }

    private JsonNode findInSquareAs(long agentId, String keyword, String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (keyword != null) {
            body.put("keyword", keyword);
        }
        JsonNode resp = callApi("/v1/api/agent-square/list", userId, body);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        for (JsonNode item : resp.get("data")) {
            if (item.get("id").asLong() == agentId) {
                return item;
            }
        }
        return null;
    }

    private void publish(String operator, long agentId, long projectId) throws Exception {
        JsonNode resp = callApi("/v1/api/agents/publish", operator,
                objectMapper.createObjectNode().put("id", agentId).put("projectId", projectId));
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
    }

    private void insertVersion(long agentId, String version) {
        AgentVersion agentVersion = new AgentVersion();
        agentVersion.setAgentId(agentId);
        agentVersion.setVersion(version);
        agentVersion.setDeclaration("{}");
        agentVersion.setCapabilityDegraded(false);
        agentVersion.setStatus(AgentVersionStatus.REGISTERED);
        agentVersion.setRegisteredBy(OWNER);
        agentVersion.setRegisteredAt(LocalDateTime.now());
        agentVersion.setCreatedBy(OWNER);
        agentVersionMapper.insert(agentVersion);
    }

    private long createProject(String userId, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "sq-p" + CODE_SEQ.incrementAndGet())
                .put("name", name);
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createAgent(String operator, long projectId, String codePrefix) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", codePrefix + CODE_SEQ.incrementAndGet())
                .put("name", "广场测试 Agent")
                .put("projectId", projectId)
                .put("description", "广场可见性测试");
        JsonNode response = callApi("/v1/api/agents/create", operator, body);
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
