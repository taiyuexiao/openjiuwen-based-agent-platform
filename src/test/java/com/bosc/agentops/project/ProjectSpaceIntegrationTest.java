package com.bosc.agentops.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.project.entity.ProjectMember;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.SubjectType;
import com.bosc.agentops.project.mapper.ProjectEnvironmentMapper;
import com.bosc.agentops.project.mapper.ProjectMemberMapper;
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
 * 模块 01 端到端集成测试：认证过滤器 + 权限切面 + 业务事务 + 审计落库全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectSpaceIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String ADMIN = "u1002";
    private static final String DEVELOPER = "u1003";
    private static final String OUTSIDER = "u1004";
    private static final String GROUP_USER = "u1005";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectMemberMapper projectMemberMapper;
    @Autowired
    private ProjectEnvironmentMapper projectEnvironmentMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;

    @Test
    void createProject_callerBecomesOwnerAndEnvsInitialized() throws Exception {
        long projectId = createProject(OWNER);

        List<ProjectMember> members = membersOf(projectId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getSubjectType()).isEqualTo(SubjectType.USER);
        assertThat(members.get(0).getSubjectId()).isEqualTo(OWNER);
        assertThat(members.get(0).getRole()).isEqualTo(Role.OWNER);

        assertThat(projectEnvironmentMapper.selectCount(
                new LambdaQueryWrapper<com.bosc.agentops.project.entity.ProjectEnvironment>()
                        .eq(com.bosc.agentops.project.entity.ProjectEnvironment::getProjectId, projectId)))
                .isEqualTo(4);
    }

    @Test
    void adminCanAddMember() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, SubjectType.USER, ADMIN, Role.ADMIN).andExpectCode(0);

        // ADMIN 有 project:member:manage，可以继续加人
        addMember(ADMIN, projectId, SubjectType.USER, DEVELOPER, Role.DEVELOPER).andExpectCode(0);

        assertThat(membersOf(projectId)).hasSize(3);
    }

    @Test
    void developerCannotAddMember_failClosed() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, SubjectType.USER, DEVELOPER, Role.DEVELOPER).andExpectCode(0);

        // DEVELOPER 无 project:member:manage → 403 业务码（fail-closed 证据）
        addMember(DEVELOPER, projectId, SubjectType.USER, OUTSIDER, Role.OPERATOR).andExpectCode(40301);

        // 完全无授权的用户同样被拒
        addMember(OUTSIDER, projectId, SubjectType.USER, OUTSIDER, Role.OPERATOR).andExpectCode(40301);

        assertThat(membersOf(projectId)).hasSize(2);
    }

    @Test
    void groupAuthorizationTakesEffect() throws Exception {
        long projectId = createProject(OWNER);

        long groupId = createGroup(OWNER, "dev-group-" + CODE_SEQ.incrementAndGet());
        operateGroupMember(OWNER, "/v1/api/user-groups/members/add", groupId, GROUP_USER).andExpectCode(0);
        addMember(OWNER, projectId, SubjectType.GROUP, String.valueOf(groupId), Role.DEVELOPER).andExpectCode(0);

        // 组内用户经组授权获得 DEVELOPER 的 project:update
        updateProject(GROUP_USER, projectId).andExpectCode(0);
        // 但 DEVELOPER 没有 project:member:manage
        addMember(GROUP_USER, projectId, SubjectType.USER, OUTSIDER, Role.OPERATOR).andExpectCode(40301);
    }

    @Test
    void removeLastOwnerRejected() throws Exception {
        long projectId = createProject(OWNER);
        removeMember(OWNER, projectId, SubjectType.USER, OWNER).andExpectCode(40902);

        // 再加一个 OWNER 后可以移除原 OWNER
        addMember(OWNER, projectId, SubjectType.USER, ADMIN, Role.OWNER).andExpectCode(0);
        removeMember(ADMIN, projectId, SubjectType.USER, OWNER).andExpectCode(0);

        // 降级最后一个 OWNER 同样被拒
        changeRole(ADMIN, projectId, SubjectType.USER, ADMIN, Role.ADMIN).andExpectCode(40902);
    }

    @Test
    void auditEventsRecordedWithRequestId() throws Exception {
        String requestId = "req-test-" + CODE_SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "audit-" + CODE_SEQ.incrementAndGet())
                .put("name", "审计测试项目");
        mockMvc.perform(post("/v1/api/projects/create")
                        .header("Authorization", "Bearer " + OWNER)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();

        List<AuditEvent> events = auditEventMapper.selectList(
                new LambdaQueryWrapper<AuditEvent>().eq(AuditEvent::getRequestId, requestId));
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getModule()).isEqualTo("project");
        assertThat(event.getAction()).isEqualTo("create");
        assertThat(event.getResourceType()).isEqualTo("project");
        assertThat(event.getUserId()).isEqualTo(OWNER);
        assertThat(event.getResult()).isEqualTo("SUCCESS");
        assertThat(event.getDetail()).contains("审计测试项目");
    }

    @Test
    void envQuotaManagePermission() throws Exception {
        long projectId = createProject(OWNER);
        addMember(OWNER, projectId, SubjectType.USER, DEVELOPER, Role.DEVELOPER).andExpectCode(0);

        ObjectNode quota = objectMapper.createObjectNode()
                .put("env", "DEV")
                .put("resourceQuota", "{\"cpu\":4,\"memoryGb\":8}");
        // OWNER 有 project:env:manage
        new CheckedResponse("/v1/api/projects/" + projectId + "/envs/set-quota", OWNER, quota).andExpectCode(0);
        // DEVELOPER 无 project:env:manage
        new CheckedResponse("/v1/api/projects/" + projectId + "/envs/set-quota", DEVELOPER, quota)
                .andExpectCode(40301);
        // DEVELOPER 有 project:read，可读环境列表
        new CheckedResponse("/v1/api/projects/" + projectId + "/envs/list", DEVELOPER,
                objectMapper.createObjectNode()).andExpectCode(0);
    }

    @Test
    void unauthenticatedRequestRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/api/projects/list")).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("code").asInt()).isEqualTo(40101);
    }

    // ---------- helpers ----------

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "p" + CODE_SEQ.incrementAndGet())
                .put("name", "测试项目")
                .put("description", "集成测试");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createGroup(String userId, String name) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("name", name);
        JsonNode response = callApi("/v1/api/user-groups/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private CheckedResponse addMember(String operator, long projectId, SubjectType subjectType,
                                      String subjectId, Role role) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("subjectType", subjectType.name())
                .put("subjectId", subjectId)
                .put("role", role.name());
        return new CheckedResponse("/v1/api/projects/" + projectId + "/members/add", operator, body);
    }

    private CheckedResponse removeMember(String operator, long projectId, SubjectType subjectType, String subjectId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("subjectType", subjectType.name())
                .put("subjectId", subjectId);
        return new CheckedResponse("/v1/api/projects/" + projectId + "/members/remove", operator, body);
    }

    private CheckedResponse changeRole(String operator, long projectId, SubjectType subjectType,
                                       String subjectId, Role role) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("subjectType", subjectType.name())
                .put("subjectId", subjectId)
                .put("role", role.name());
        return new CheckedResponse("/v1/api/projects/" + projectId + "/members/change-role", operator, body);
    }

    private CheckedResponse operateGroupMember(String operator, String url, long groupId, String memberUserId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("groupId", groupId)
                .put("userId", memberUserId);
        return new CheckedResponse(url, operator, body);
    }

    private CheckedResponse updateProject(String operator, long projectId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("id", projectId)
                .put("name", "改名后的项目")
                .put("description", "更新");
        return new CheckedResponse("/v1/api/projects/update", operator, body);
    }

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<ProjectMember> membersOf(long projectId) {
        return projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
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
