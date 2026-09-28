package com.bosc.agentops.common.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 权限矩阵端到端测试：矩阵结构读取、平台管理员整体替换角色权限（影响后续权限判定）、
 * 非管理员 40301、OWNER 角色 40902（防锁死）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionMatrixIntegrationTest {

    private static final String PLATFORM_ADMIN = "u1001";
    private static final String PROJECT_OWNER = "u1002";
    private static final String DEVELOPER = "u1003";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void matrixReturnsAllPermissionsAndFourRoles() throws Exception {
        JsonNode resp = callApi("/v1/api/permissions/matrix", PROJECT_OWNER, null);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);

        JsonNode data = resp.get("data");
        assertThat(data.get("permissions").isArray()).isTrue();
        List<String> permissions = new ArrayList<>();
        data.get("permissions").forEach(p -> permissions.add(p.asText()));
        assertThat(permissions).contains("project:read", "asset:create", "agent:update", "obs:read");

        assertThat(data.get("roles")).hasSize(4);
        List<String> roleNames = new ArrayList<>();
        data.get("roles").forEach(r -> roleNames.add(r.get("role").asText()));
        assertThat(roleNames).containsExactly("OWNER", "ADMIN", "DEVELOPER", "OPERATOR");

        JsonNode owner = data.get("roles").get(0);
        assertThat(owner.get("permissions").toString()).contains("project:archive");
        // 每个角色的权限点都属于全集
        for (JsonNode role : data.get("roles")) {
            for (JsonNode p : role.get("permissions")) {
                assertThat(permissions).contains(p.asText());
            }
        }
    }

    @Test
    void adminUpdateRoleTakesEffectOnPermissionCheck() throws Exception {
        // PROJECT_OWNER 建项目并加 DEVELOPER 成员
        long projectId = createProject(PROJECT_OWNER);
        JsonNode addResp = callApi("/v1/api/projects/" + projectId + "/members/add", PROJECT_OWNER,
                objectMapper.createObjectNode()
                        .put("subjectType", "USER").put("subjectId", DEVELOPER).put("role", "DEVELOPER"));
        assertThat(addResp.get("code").asInt()).isEqualTo(0);

        // 基线：DEVELOPER 有 agent:create
        assertThat(createAgent(DEVELOPER, projectId).get("code").asInt()).isEqualTo(0);

        // 读取 DEVELOPER 原权限集合并摘除 agent:create
        List<String> original = rolePermissions("DEVELOPER");
        assertThat(original).contains("agent:create");
        List<String> reduced = original.stream().filter(p -> !p.equals("agent:create")).toList();

        ObjectNode updateBody = objectMapper.createObjectNode().put("role", "DEVELOPER");
        ArrayNode reducedArray = updateBody.putArray("permissions");
        reduced.forEach(reducedArray::add);
        JsonNode updateResp = callApi("/v1/api/permissions/roles/update", PLATFORM_ADMIN, updateBody);
        assertThat(updateResp.get("code").asInt()).as(String.valueOf(updateResp)).isEqualTo(0);
        try {
            // 更新立即生效：DEVELOPER 创建 Agent 被拒
            assertThat(createAgent(DEVELOPER, projectId).get("code").asInt()).isEqualTo(40301);
            // 矩阵反映新集合
            assertThat(rolePermissions("DEVELOPER")).doesNotContain("agent:create");
        } finally {
            // 还原，避免影响共享库上的其他测试
            ObjectNode restoreBody = objectMapper.createObjectNode().put("role", "DEVELOPER");
            ArrayNode restoreArray = restoreBody.putArray("permissions");
            original.forEach(restoreArray::add);
            JsonNode restoreResp = callApi("/v1/api/permissions/roles/update", PLATFORM_ADMIN, restoreBody);
            assertThat(restoreResp.get("code").asInt()).isEqualTo(0);
        }
        assertThat(createAgent(DEVELOPER, projectId).get("code").asInt()).isEqualTo(0);
    }

    @Test
    void nonAdminCannotUpdateRole() throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("role", "OPERATOR");
        body.putArray("permissions").add("project:read");
        JsonNode resp = callApi("/v1/api/permissions/roles/update", PROJECT_OWNER, body);
        assertThat(resp.get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void ownerRoleUpdateRejectedToPreventLockout() throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("role", "OWNER");
        body.putArray("permissions").add("project:read");
        JsonNode resp = callApi("/v1/api/permissions/roles/update", PLATFORM_ADMIN, body);
        assertThat(resp.get("code").asInt()).isEqualTo(40902);
    }

    // ---------- helpers ----------

    private List<String> rolePermissions(String role) throws Exception {
        JsonNode resp = callApi("/v1/api/permissions/matrix", PLATFORM_ADMIN, null);
        assertThat(resp.get("code").asInt()).isEqualTo(0);
        for (JsonNode r : resp.get("data").get("roles")) {
            if (role.equals(r.get("role").asText())) {
                List<String> permissions = new ArrayList<>();
                r.get("permissions").forEach(p -> permissions.add(p.asText()));
                return permissions;
            }
        }
        throw new AssertionError("角色不存在: " + role);
    }

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "pm-p" + CODE_SEQ.incrementAndGet())
                .put("name", "权限矩阵测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private JsonNode createAgent(String operator, long projectId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "pm-a" + CODE_SEQ.incrementAndGet())
                .put("name", "权限测试 Agent")
                .put("projectId", projectId);
        return callApi("/v1/api/agents/create", operator, body);
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
