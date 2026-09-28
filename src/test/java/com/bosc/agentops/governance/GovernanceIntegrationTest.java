package com.bosc.agentops.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.mapper.DeploymentMapper;
import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.entity.InvocationStatus;
import com.bosc.agentops.governance.entity.RouteStatus;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.governance.mapper.ServiceRouteMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 07 端到端集成测试（RANDOM_PORT 真实 HTTP）：
 * 路由 sync（RUNNING 部署 → 路由生成/幂等/旧路由 DRAINED）、invoke 全链路（真实 HTTP 转发到
 * 测试内嵌 HttpServer）、403/429/404/502 各拒绝分支、SSE 逐帧透传、mcp-check 三态、
 * agent-auth 允许/拒绝、服务目录、管理面权限（DEVELOPER 只读、OPERATOR 可 sync）。
 * 独立 H2 库（govtest），避免与其他测试类共享的默认库互相污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:govtest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,ROLE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovernanceIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String ADMIN = "u1002";
    private static final String DEVELOPER = "u1003";
    private static final String OPERATOR = "u1006";

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DeploymentMapper deploymentMapper;
    @Autowired
    private ServiceRouteMapper routeMapper;
    @Autowired
    private InvocationRecordMapper invocationRecordMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;

    private HttpServer upstream;
    private int upstreamPort;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void startUpstream() throws Exception {
        upstream = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        upstream.setExecutor(Executors.newCachedThreadPool());
        upstreamPort = upstream.getAddress().getPort();
        upstream.createContext("/invoke", exchange -> {
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            byte[] resp = ("{\"echo\":" + new String(reqBody, StandardCharsets.UTF_8)
                    + ",\"from\":\"upstream\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().flush();
            exchange.close();
        });
        upstream.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            var out = exchange.getResponseBody();
            try {
                for (int i = 1; i <= 3; i++) {
                    out.write(("data: evt-" + i + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                exchange.close();
            }
        });
        upstream.createContext("/boom", exchange -> {
            byte[] resp = "{\"error\":\"upstream broken\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().flush();
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    // ---------- routes/sync ----------

    @Test
    void syncFromRunningDeploymentIdempotentAndDrainOldRoute() throws Exception {
        Fixture fx = deployAgent("1.0.0");

        // sync → 生成 ACTIVE 路由 revision=1
        JsonNode synced = callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "1.0.0"));
        assertThat(synced.get("code").asInt()).as(String.valueOf(synced)).isEqualTo(0);
        long routeV1 = synced.get("data").get("id").asLong();
        assertThat(synced.get("data").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(synced.get("data").get("routeRevision").asInt()).isEqualTo(1);
        assertThat(synced.get("data").get("deploymentId").asLong()).isEqualTo(fx.deploymentId);

        // 幂等：同一 deployment 重复 sync 不产生新 revision
        JsonNode again = callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "1.0.0"));
        assertThat(again.get("data").get("id").asLong()).isEqualTo(routeV1);
        assertThat(again.get("data").get("routeRevision").asInt()).isEqualTo(1);
        assertThat(routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                .eq(ServiceRoute::getAgentId, fx.agentId))).hasSize(1);

        // 无 RUNNING 部署的 agent+env → 40401
        assertThat(callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "PROD").put("agentVersion", "1.0.0")).get("code").asInt()).isEqualTo(40401);

        // 新版本部署 + sync：旧路由 DRAINED，新路由 ACTIVE revision=2
        deployVersion(fx, "1.0.1");
        JsonNode syncedV2 = callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "1.0.1"));
        assertThat(syncedV2.get("code").asInt()).as(String.valueOf(syncedV2)).isEqualTo(0);
        assertThat(syncedV2.get("data").get("routeRevision").asInt()).isEqualTo(2);
        assertThat(syncedV2.get("data").get("status").asText()).isEqualTo("ACTIVE");
        ServiceRoute oldRoute = routeMapper.selectById(routeV1);
        assertThat(oldRoute.getStatus()).isEqualTo(RouteStatus.DRAINED);

        // 版本与 RUNNING 部署不一致 → 40001
        assertThat(callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "9.9.9")).get("code").asInt()).isEqualTo(40001);

        // 审计：route.sync 落 governance 模块审计
        List<AuditEvent> syncAudits = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "governance")
                .eq(AuditEvent::getAction, "route.sync"));
        assertThat(syncAudits.size()).isGreaterThanOrEqualTo(2);
    }

    // ---------- invoke 全链路 ----------

    @Test
    void invokeFullChainForwardAndRecord() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/invoke");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "USER", "u9001", null, 600, 5000);

        HttpResponse<String> resp = invokePost(fx.agentCode, "UAT", "USER", "u9001",
                null, null, "{\"q\":\"hi\"}");
        assertThat(resp.statusCode()).as(resp.body()).isEqualTo(200);
        assertThat(resp.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/json"));
        assertThat(resp.body()).contains("\"echo\":{\"q\":\"hi\"}").contains("\"from\":\"upstream\"");
        assertThat(resp.headers().firstValue("X-Trace-Id")).isPresent();

        // 记录落库：SUCCESS + routeId + 延迟
        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(1);
        InvocationRecord record = records.get(0);
        assertThat(record.getStatus()).isEqualTo(InvocationStatus.SUCCESS);
        assertThat(record.getCallerType().name()).isEqualTo("USER");
        assertThat(record.getCallerId()).isEqualTo("u9001");
        assertThat(record.getRouteId()).isNotNull();
        assertThat(record.getAgentVersion()).isEqualTo("1.0.0");
        assertThat(record.getTraceId()).isNotBlank();
        assertThat(record.getLatencyMs()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void invokeRejectedWithoutPolicy403() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/invoke");
        syncRoute(fx, "1.0.0");

        // 无 CallerPolicy → 403 + 40301，落 REJECTED 记录
        HttpResponse<String> resp = invokePost(fx.agentCode, "UAT", "USER", "nobody", null, null, "{}");
        assertThat(resp.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(resp.body()).get("code").asInt()).isEqualTo(40301);
        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(InvocationStatus.REJECTED);

        // 缺少调用方身份头 → 401 + 40101（身份缺失不落记录）
        HttpResponse<String> noIdentity = invokePost(fx.agentCode, "UAT", null, null, null, null, "{}");
        assertThat(noIdentity.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(noIdentity.body()).get("code").asInt()).isEqualTo(40101);
        assertThat(recordsOf(fx.agentId)).hasSize(1);

        // 策略 revoke 后 → 403
        grantCaller(fx, "USER", "u9001", null, 600, 5000);
        JsonNode policies = callApi("/v1/api/caller-policies/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId));
        long policyId = policies.get("data").get(0).get("id").asLong();
        assertThat(callApi("/v1/api/caller-policies/revoke", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("id", policyId)).get("code").asInt()).isEqualTo(0);
        assertThat(invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}").statusCode())
                .isEqualTo(403);
    }

    @Test
    void invokeRateLimited429() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/invoke");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "HIAGENT", "hiagent-sys", null, 2, 5000);

        assertThat(invokePost(fx.agentCode, "UAT", "HIAGENT", "hiagent-sys", null, null, "{}").statusCode())
                .isEqualTo(200);
        assertThat(invokePost(fx.agentCode, "UAT", "HIAGENT", "hiagent-sys", null, null, "{}").statusCode())
                .isEqualTo(200);
        HttpResponse<String> limited = invokePost(fx.agentCode, "UAT", "HIAGENT", "hiagent-sys",
                null, null, "{}");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(objectMapper.readTree(limited.body()).get("code").asInt()).isEqualTo(42901);

        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(3);
        assertThat(records.stream().filter(r -> r.getStatus() == InvocationStatus.REJECTED)).hasSize(1);
        assertThat(records.stream().filter(r -> r.getStatus() == InvocationStatus.SUCCESS)).hasSize(2);
    }

    @Test
    void invokeNoActiveRoute404AndVersionHeaderRouting() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/invoke");
        grantCaller(fx, "USER", "u9001", null, 600, 5000);

        // 未 sync → 无 ACTIVE 路由 → 404 + 40401
        HttpResponse<String> noRoute = invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}");
        assertThat(noRoute.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(noRoute.body()).get("code").asInt()).isEqualTo(40401);

        // sync v1 → 可路由；部署 v2 + sync v2 后旧版本 header 路由 404，指定新版本可路由
        syncRoute(fx, "1.0.0");
        assertThat(invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}").statusCode())
                .isEqualTo(200);
        deployVersion(fx, "1.0.1");
        pointDeployment(latestDeploymentId(fx), "/invoke");
        syncRoute(fx, "1.0.1");
        assertThat(invokePost(fx.agentCode, "UAT", "USER", "u9001", "1.0.0", null, "{}").statusCode())
                .isEqualTo(404);
        assertThat(invokePost(fx.agentCode, "UAT", "USER", "u9001", "1.0.1", null, "{}").statusCode())
                .isEqualTo(200);
        assertThat(invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}").statusCode())
                .isEqualTo(200);
    }

    @Test
    void invokeSharedTokenEnforced() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/invoke");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "AGENT", "caller-ag-1", "s3cret-token", 600, 5000);

        // 策略配置了 sharedToken：缺失/错误 → 401；正确 → 200
        assertThat(invokePost(fx.agentCode, "UAT", "AGENT", "caller-ag-1", null, null, "{}").statusCode())
                .isEqualTo(401);
        assertThat(invokePost(fx.agentCode, "UAT", "AGENT", "caller-ag-1", null, "wrong", "{}").statusCode())
                .isEqualTo(401);
        assertThat(invokePost(fx.agentCode, "UAT", "AGENT", "caller-ag-1", null, "s3cret-token", "{}")
                .statusCode()).isEqualTo(200);
    }

    @Test
    void invokeUpstreamUnreachable502() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeploymentTo(fx.deploymentId, "http://127.0.0.1:1/unreachable");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "USER", "u9001", null, 600, 3000);

        HttpResponse<String> resp = invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}");
        assertThat(resp.statusCode()).isEqualTo(502);
        assertThat(objectMapper.readTree(resp.body()).get("code").asInt()).isEqualTo(50201);
        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(InvocationStatus.FAILED);
        assertThat(records.get(0).getRouteId()).isNotNull();
    }

    @Test
    void invokeSsePassThrough() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/sse");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "HIAGENT", "hiagent-sys", null, 600, 10000);

        HttpRequest request = invokeRequest(fx.agentCode, "UAT", "HIAGENT", "hiagent-sys", null, null, "{}")
                .build();
        HttpResponse<java.io.InputStream> resp = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("text/event-stream"));

        // 逐条 data 帧到达（流式：首帧远早于末帧，证明未缓冲整体响应）
        List<String> frames = new ArrayList<>();
        List<Long> arrivals = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    frames.add(line);
                    arrivals.add(System.currentTimeMillis());
                }
            }
        }
        assertThat(frames).containsExactly("data: evt-1", "data: evt-2", "data: evt-3");
        assertThat(arrivals.get(2) - arrivals.get(0)).isGreaterThanOrEqualTo(400);

        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(InvocationStatus.SUCCESS);
    }

    @Test
    void invokeUpstreamServerErrorRecordedFailed() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        pointDeployment(fx.deploymentId, "/boom");
        syncRoute(fx, "1.0.0");
        grantCaller(fx, "USER", "u9001", null, 600, 5000);

        // 上游 5xx：状态与响应体透传，记录 FAILED
        HttpResponse<String> resp = invokePost(fx.agentCode, "UAT", "USER", "u9001", null, null, "{}");
        assertThat(resp.statusCode()).isEqualTo(500);
        assertThat(resp.body()).contains("upstream broken");
        List<InvocationRecord> records = recordsOf(fx.agentId);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo(InvocationStatus.FAILED);
        assertThat(records.get(0).getError()).contains("500");
    }

    // ---------- mcp-check / agent-auth ----------

    @Test
    void mcpCheckThreeStates() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        long assetId = 900001L;

        // 无策略 → 默认拒绝（fail-closed）
        JsonNode noPolicy = runtimePost("/v1/mcp-check", objectMapper.createObjectNode()
                .put("agentId", fx.agentId).put("toolAssetId", assetId).put("env", "UAT"));
        assertThat(noPolicy.get("code").asInt()).isEqualTo(0);
        assertThat(noPolicy.get("data").get("allowed").asBoolean()).isFalse();
        assertThat(noPolicy.get("data").get("reason").asText()).contains("fail-closed");

        // 显式拒绝
        assertThat(callApi("/v1/api/mcp-policies/grant", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("assetId", assetId).put("env", "UAT").put("allowed", false))
                .get("code").asInt()).isEqualTo(0);
        JsonNode denied = runtimePost("/v1/mcp-check", objectMapper.createObjectNode()
                .put("agentId", fx.agentId).put("toolAssetId", assetId).put("env", "UAT"));
        assertThat(denied.get("data").get("allowed").asBoolean()).isFalse();
        assertThat(denied.get("data").get("reason").asText()).contains("显式拒绝");

        // 允许（同 key upsert 更新 allowed）
        assertThat(callApi("/v1/api/mcp-policies/grant", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("assetId", assetId).put("env", "UAT").put("allowed", true))
                .get("code").asInt()).isEqualTo(0);
        JsonNode allowed = runtimePost("/v1/mcp-check", objectMapper.createObjectNode()
                .put("agentId", fx.agentId).put("toolAssetId", assetId).put("env", "UAT"));
        assertThat(allowed.get("data").get("allowed").asBoolean()).isTrue();

        // 其他环境仍 fail-closed
        JsonNode otherEnv = runtimePost("/v1/mcp-check", objectMapper.createObjectNode()
                .put("agentId", fx.agentId).put("toolAssetId", assetId).put("env", "PROD"));
        assertThat(otherEnv.get("data").get("allowed").asBoolean()).isFalse();
    }

    @Test
    void agentAuthAllowAndDeny() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        grantCaller(fx, "AGENT", "caller-ag-9", null, 600, 5000);

        JsonNode allowed = runtimePost("/v1/agent-auth/check", objectMapper.createObjectNode()
                .put("callerAgentCode", "caller-ag-9").put("calleeAgentCode", fx.agentCode)
                .put("env", "UAT"));
        assertThat(allowed.get("code").asInt()).isEqualTo(0);
        assertThat(allowed.get("data").get("allowed").asBoolean()).isTrue();

        // 无策略的调用方 → 拒绝；不存在的被调 Agent → 拒绝
        JsonNode denied = runtimePost("/v1/agent-auth/check", objectMapper.createObjectNode()
                .put("callerAgentCode", "stranger").put("calleeAgentCode", fx.agentCode)
                .put("env", "UAT"));
        assertThat(denied.get("data").get("allowed").asBoolean()).isFalse();
        JsonNode noCallee = runtimePost("/v1/agent-auth/check", objectMapper.createObjectNode()
                .put("callerAgentCode", "caller-ag-9").put("calleeAgentCode", "no-such-agent")
                .put("env", "UAT"));
        assertThat(noCallee.get("data").get("allowed").asBoolean()).isFalse();
    }

    // ---------- 服务目录 ----------

    @Test
    void serviceDirectoryListsActiveRoutes() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        syncRoute(fx, "1.0.0");

        JsonNode directory = callApi("/v1/api/service-directory/list", OWNER,
                objectMapper.createObjectNode().put("projectId", fx.projectId));
        assertThat(directory.get("code").asInt()).as(String.valueOf(directory)).isEqualTo(0);
        assertThat(directory.get("data")).hasSize(1);
        JsonNode entry = directory.get("data").get(0);
        assertThat(entry.get("agentCode").asText()).isEqualTo(fx.agentCode);
        assertThat(entry.get("env").asText()).isEqualTo("UAT");
        assertThat(entry.get("agentVersion").asText()).isEqualTo("1.0.0");
        assertThat(entry.get("address").asText()).isEqualTo("/v1/invoke/" + fx.agentCode + "/UAT");
        assertThat(entry.get("authType").asText()).isEqualTo("Bearer");

        // env 过滤：PROD 无路由 → 空
        JsonNode prodOnly = callApi("/v1/api/service-directory/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("env", "PROD"));
        assertThat(prodOnly.get("data")).isEmpty();
    }

    // ---------- 管理面权限 ----------

    @Test
    void managementPlanePermissionMatrix() throws Exception {
        Fixture fx = deployAgent("1.0.0");
        addMember(fx.projectId, DEVELOPER, "DEVELOPER");
        addMember(fx.projectId, ADMIN, "ADMIN");
        addMember(fx.projectId, OPERATOR, "OPERATOR");

        // DEVELOPER：gov:read 只读（list 放行），无 gov:route（sync 拒绝）、无 gov:policy（grant 拒绝）
        assertThat(callApi("/v1/api/routes/list", DEVELOPER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId)).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/routes/sync", DEVELOPER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "1.0.0")).get("code").asInt()).isEqualTo(40301);
        assertThat(callApi("/v1/api/caller-policies/grant", DEVELOPER, grantBody(fx, "USER", "u9001"))
                .get("code").asInt()).isEqualTo(40301);

        // OPERATOR：gov:read + gov:route（可 sync），无 gov:policy
        JsonNode opSync = callApi("/v1/api/routes/sync", OPERATOR, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", "1.0.0"));
        assertThat(opSync.get("code").asInt()).as(String.valueOf(opSync)).isEqualTo(0);
        assertThat(callApi("/v1/api/mcp-policies/grant", OPERATOR, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("assetId", 1L).put("env", "UAT").put("allowed", true))
                .get("code").asInt()).isEqualTo(40301);

        // ADMIN：全量（gov:policy 放行）
        assertThat(callApi("/v1/api/caller-policies/grant", ADMIN, grantBody(fx, "USER", "u9001"))
                .get("code").asInt()).isEqualTo(0);

        // 非成员 fail-closed
        assertThat(callApi("/v1/api/routes/list", "u1004", objectMapper.createObjectNode()
                .put("projectId", fx.projectId)).get("code").asInt()).isEqualTo(40301);
    }

    // ---------- fixtures / helpers ----------

    private record Fixture(long projectId, long agentId, String agentCode, long targetId,
                           long deploymentId) {
    }

    /** 完整交付链路：建项目/Agent/目标 → gate → approve → deploy（RUNNING），返回 fixture */
    private Fixture deployAgent(String version) throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        String agentCode = lastAgentCode;
        long targetId = createTarget("UAT");
        attach(projectId, targetId);
        bindApp(projectId, agentId);
        confirmLevel(projectId, agentId, "P2");
        registerVersion(projectId, agentId, version);
        long artifactId = registerArtifact(projectId, agentId, version);
        long releaseId = createRelease(projectId, agentId, version, artifactId, targetId);
        gateApproveDeploy(projectId, releaseId);
        return new Fixture(projectId, agentId, agentCode, targetId, latestDeploymentId(projectId, releaseId));
    }

    /** 同一 Agent 追加部署一个新版本（部署成功后旧实例自动停止） */
    private void deployVersion(Fixture fx, String version) throws Exception {
        registerVersion(fx.projectId, fx.agentId, version);
        long artifactId = registerArtifact(fx.projectId, fx.agentId, version);
        long releaseId = createRelease(fx.projectId, fx.agentId, version, artifactId, fx.targetId);
        gateApproveDeploy(fx.projectId, releaseId);
    }

    private long latestDeploymentId(Fixture fx) throws Exception {
        JsonNode deployments = callApi("/v1/api/deployments/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId));
        long latest = -1;
        for (JsonNode d : deployments.get("data")) {
            if ("RUNNING".equals(d.get("status").asText()) && d.get("id").asLong() > latest) {
                latest = d.get("id").asLong();
            }
        }
        assertThat(latest).isPositive();
        return latest;
    }

    private long latestDeploymentId(long projectId, long releaseId) throws Exception {
        JsonNode deployments = callApi("/v1/api/deployments/list", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("releaseId", releaseId));
        assertThat(deployments.get("data")).hasSize(1);
        assertThat(deployments.get("data").get(0).get("status").asText()).isEqualTo("RUNNING");
        return deployments.get("data").get(0).get("id").asLong();
    }

    private String lastAgentCode;

    private long createProject() throws Exception {
        JsonNode response = callApi("/v1/api/projects/create", OWNER, objectMapper.createObjectNode()
                .put("code", "gov-p" + SEQ.incrementAndGet()).put("name", "治理测试项目"));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void addMember(long projectId, String subjectId, String role) throws Exception {
        JsonNode response = callApi("/v1/api/projects/" + projectId + "/members/add", OWNER,
                objectMapper.createObjectNode().put("subjectType", "USER")
                        .put("subjectId", subjectId).put("role", role));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long createAgent(long projectId) throws Exception {
        lastAgentCode = "gov-ag-" + SEQ.incrementAndGet();
        JsonNode response = callApi("/v1/api/agents/create", OWNER, objectMapper.createObjectNode()
                .put("code", lastAgentCode).put("name", "治理测试 Agent")
                .put("projectId", projectId).put("accessMode", "ADAPTED"));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void registerVersion(long projectId, long agentId, String version) throws Exception {
        JsonNode response = callApi("/v1/api/agents/" + agentId + "/versions/register", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("version", version));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long createTarget(String env) throws Exception {
        String code = "gov-t-" + SEQ.incrementAndGet();
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", code).put("name", "目标-" + code)
                .put("env", env).put("cluster", "k8s-" + env.toLowerCase())
                .put("namespace", "agentops");
        body.putObject("baseResource").put("cpu", "1").put("memory", "2Gi");
        JsonNode response = callApi("/v1/api/deploy-targets/create", OWNER, body);
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void attach(long projectId, long targetId) throws Exception {
        JsonNode response = callApi("/v1/api/projects/" + projectId + "/deploy-targets/attach", OWNER,
                objectMapper.createObjectNode().put("targetId", targetId));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private void bindApp(long projectId, long agentId) throws Exception {
        JsonNode response = callApi("/v1/api/bindings/bind", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("appCode", "APP-CORE-001"));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private void confirmLevel(long projectId, long agentId, String level) throws Exception {
        JsonNode response = callApi("/v1/api/agent-levels/confirm", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("level", level)
                .put("source", "评审单-" + SEQ.incrementAndGet()));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    private long registerArtifact(long projectId, long agentId, String version) throws Exception {
        JsonNode response = callApi("/v1/api/artifacts/register", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", version)
                .put("codeCommit", "c" + SEQ.incrementAndGet())
                .put("imageDigest", "sha256:" + SEQ.incrementAndGet())
                .put("configDigest", "sha256:cfg" + SEQ.incrementAndGet())
                .put("evaluationRef", "EVAL-" + SEQ.incrementAndGet()));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createRelease(long projectId, long agentId, String version, long artifactId, long targetId)
            throws Exception {
        JsonNode response = callApi("/v1/api/releases/create", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("agentId", agentId).put("agentVersion", version)
                .put("artifactId", artifactId).put("targetId", targetId));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private void gateApproveDeploy(long projectId, long releaseId) throws Exception {
        ObjectNode idReq = objectMapper.createObjectNode().put("projectId", projectId).put("id", releaseId);
        assertThat(callApi("/v1/api/releases/gate", OWNER, idReq).get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/releases/approve", OWNER, objectMapper.createObjectNode()
                .put("projectId", projectId).put("id", releaseId)
                .put("approvalRef", "FLOW-" + SEQ.incrementAndGet())).get("code").asInt()).isEqualTo(0);
        JsonNode deployed = callApi("/v1/api/releases/deploy", OWNER, idReq);
        assertThat(deployed.get("code").asInt()).as(String.valueOf(deployed)).isEqualTo(0);
    }

    private void syncRoute(Fixture fx, String version) throws Exception {
        JsonNode synced = callApi("/v1/api/routes/sync", OWNER, objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("agentVersion", version));
        assertThat(synced.get("code").asInt()).as(String.valueOf(synced)).isEqualTo(0);
    }

    private void grantCaller(Fixture fx, String callerType, String callerId, String sharedToken,
                             int rateLimit, int timeoutMs) throws Exception {
        ObjectNode body = grantBody(fx, callerType, callerId);
        if (sharedToken != null) {
            body.put("sharedToken", sharedToken);
        }
        body.put("rateLimitPerMin", rateLimit).put("timeoutMs", timeoutMs);
        JsonNode granted = callApi("/v1/api/caller-policies/grant", OWNER, body);
        assertThat(granted.get("code").asInt()).as(String.valueOf(granted)).isEqualTo(0);
    }

    private ObjectNode grantBody(Fixture fx, String callerType, String callerId) {
        return objectMapper.createObjectNode()
                .put("projectId", fx.projectId).put("agentId", fx.agentId)
                .put("env", "UAT").put("callerType", callerType).put("callerId", callerId);
    }

    /** 将部署实例指到测试内嵌上游（stub.local 为伪地址，测试需真实 HTTP 转发目标） */
    private void pointDeployment(long deploymentId, String path) {
        pointDeploymentTo(deploymentId, "http://127.0.0.1:" + upstreamPort + path);
    }

    private void pointDeploymentTo(long deploymentId, String url) {
        Deployment deployment = deploymentMapper.selectById(deploymentId);
        deployment.setInstanceUrl(url);
        deploymentMapper.updateById(deployment);
    }

    private List<InvocationRecord> recordsOf(long agentId) {
        return invocationRecordMapper.selectList(new LambdaQueryWrapper<InvocationRecord>()
                .eq(InvocationRecord::getAgentId, agentId).orderByAsc(InvocationRecord::getId));
    }

    // ---------- HTTP helpers ----------

    private JsonNode callApi(String url, String userId, ObjectNode body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(url,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(resp.getBody());
    }

    private JsonNode runtimePost(String path, ObjectNode body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(path,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(resp.getBody());
    }

    private HttpResponse<String> invokePost(String agentCode, String env, String callerType,
                                            String callerId, String version, String sharedToken,
                                            String body) throws Exception {
        return httpClient.send(invokeRequest(agentCode, env, callerType, callerId, version, sharedToken, body)
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder invokeRequest(String agentCode, String env, String callerType,
                                              String callerId, String version, String sharedToken, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/v1/invoke/" + agentCode + "/" + env))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (callerId != null) {
            builder.header("X-Caller-Id", callerId);
        }
        if (callerType != null) {
            builder.header("X-Caller-Type", callerType);
        }
        if (version != null) {
            builder.header("X-Agent-Version", version);
        }
        if (sharedToken != null) {
            builder.header("Authorization", "Bearer " + sharedToken);
        }
        return builder;
    }
}
