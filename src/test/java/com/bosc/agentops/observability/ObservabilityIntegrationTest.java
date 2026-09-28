package com.bosc.agentops.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.entity.InvocationStatus;
import com.bosc.agentops.governance.entity.PolicyStatus;
import com.bosc.agentops.governance.entity.RouteStatus;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.mapper.CallerPolicyMapper;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.governance.mapper.ServiceRouteMapper;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.mapper.TraceSpanMapper;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块 08 端到端集成测试（RANDOM_PORT 真实 HTTP）：
 * OTLP 接收（agent.id/service.name 映射、丢弃计数、写脱敏、根 span 补记 InvocationRecord）、
 * traces/query 与 {traceId} 链路还原（项目隔离 + 403）、audit/query 兜底脱敏、
 * 告警 webhook→handle、处置动作（disable-route 跨模块置 DRAINED、revoke-caller 吊销生效）、
 * 机器通道 token 校验、组件 health 聚合（内嵌 HttpServer）。
 * 独立 H2 库（obstest），避免与其他测试类共享的默认库互相污染。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:obstest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,ROLE")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObservabilityIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String DEVELOPER = "u1003";
    private static final String OUTSIDER = "u1004";
    private static final String OPERATOR_B = "u1005";
    private static final String OPERATOR = "u1006";
    private static final String MACHINE_TOKEN = "dev-machine-token";

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TraceSpanMapper traceSpanMapper;
    @Autowired
    private ServiceRouteMapper routeMapper;
    @Autowired
    private CallerPolicyMapper callerPolicyMapper;
    @Autowired
    private InvocationRecordMapper invocationRecordMapper;
    @Autowired
    private AuditEventMapper auditEventMapper;
    @Autowired
    private AuditService auditService;

    private HttpServer healthServer;
    private int healthPort;

    @BeforeAll
    void startHealthServer() throws Exception {
        healthServer = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        healthServer.setExecutor(Executors.newCachedThreadPool());
        healthPort = healthServer.getAddress().getPort();
        healthServer.createContext("/health", exchange -> {
            byte[] resp = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().flush();
            exchange.close();
        });
        healthServer.start();
    }

    @AfterAll
    void stopHealthServer() {
        if (healthServer != null) {
            healthServer.stop(0);
        }
    }

    // ---------- OTLP 接收 ----------

    @Test
    void otlpIngestMapsAgentMasksAndCountsDropped() throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        String agentCode = lastAgentCode;
        String traceId = "t-otlp-" + SEQ.incrementAndGet();

        String body = """
                {"resourceSpans":[
                  {"resource":{"attributes":[
                      {"key":"agent.id","value":{"stringValue":"%d"}},
                      {"key":"deployment.environment","value":{"stringValue":"uat"}}]},
                   "scopeSpans":[{"spans":[
                      {"traceId":"%s","spanId":"s1","name":"agent.run","kind":2,
                       "startTimeUnixNano":"1000","endTimeUnixNano":"2000",
                       "attributes":[{"key":"password","value":{"stringValue":"p@ss"}},
                                     {"key":"model","value":{"stringValue":"gpt-4"}}],
                       "status":{"code":1}},
                      {"traceId":"%s","spanId":"s2","parentSpanId":"s1","name":"tool.call",
                       "startTimeUnixNano":"1100","endTimeUnixNano":"1500",
                       "attributes":[{"key":"agentops.span.kind","value":{"stringValue":"TOOL"}},
                                     {"key":"Authorization","value":{"stringValue":"Bearer x"}}],
                       "status":{"code":2}}
                   ]}]},
                  {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"%s"}}]},
                   "scopeSpans":[{"spans":[{"traceId":"%s","spanId":"s3","name":"svc.match",
                       "startTimeUnixNano":"3000","endTimeUnixNano":"4000"}]}]},
                  {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"unknown-svc"}}]},
                   "scopeSpans":[{"spans":[{"traceId":"%s","spanId":"s9","name":"dropped"}]}]}
                ]}
                """.formatted(agentId, traceId, traceId, agentCode, traceId, traceId);

        JsonNode resp = machinePost("/v1/otlp/v1/traces", MACHINE_TOKEN, body);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        assertThat(resp.get("data").get("receivedCount").asInt()).isEqualTo(4);
        assertThat(resp.get("data").get("storedCount").asInt()).isEqualTo(3);
        assertThat(resp.get("data").get("droppedCount").asInt()).isEqualTo(1);

        List<TraceSpan> spans = spansOf(traceId);
        assertThat(spans).hasSize(3);
        TraceSpan root = spans.stream().filter(s -> "s1".equals(s.getSpanId())).findFirst().orElseThrow();
        assertThat(root.getAgentId()).isEqualTo(agentId);
        assertThat(root.getEnv()).isEqualTo("UAT");
        assertThat(root.getSpanKind().name()).isEqualTo("AGENT");
        assertThat(root.getStatus()).isEqualTo("OK");
        assertThat(root.getParentSpanId()).isNull();
        // 写脱敏：敏感键值为 ***，普通键保留
        assertThat(root.getAttrs()).contains("\"password\":\"***\"").contains("\"model\":\"gpt-4\"")
                .doesNotContain("p@ss");

        TraceSpan tool = spans.stream().filter(s -> "s2".equals(s.getSpanId())).findFirst().orElseThrow();
        assertThat(tool.getSpanKind().name()).isEqualTo("TOOL");
        assertThat(tool.getStatus()).isEqualTo("ERROR");
        assertThat(tool.getParentSpanId()).isEqualTo("s1");
        assertThat(tool.getAttrs()).contains("\"Authorization\":\"***\"").doesNotContain("Bearer x");

        // service.name 匹配 agent code；env 缺省 DEV
        TraceSpan byCode = spans.stream().filter(s -> "s3".equals(s.getSpanId())).findFirst().orElseThrow();
        assertThat(byCode.getAgentId()).isEqualTo(agentId);
        assertThat(byCode.getEnv()).isEqualTo("DEV");
    }

    @Test
    void otlpRootSpanBackfillsFromInvocationRecord() throws Exception {
        long projectId = createProjectQuietly();
        long agentId = createAgentQuietly(projectId);
        String traceId = "t-inv-" + SEQ.incrementAndGet();

        InvocationRecord record = new InvocationRecord();
        record.setTraceId(traceId);
        record.setAgentId(agentId);
        record.setEnv("UAT");
        record.setStatus(InvocationStatus.SUCCESS);
        invocationRecordMapper.insert(record);

        // 根 span 无任何 agent 映射线索：按 traceId 补记关联
        String body = """
                {"resourceSpans":[{"resource":{"attributes":[]},
                 "scopeSpans":[{"spans":[{"traceId":"%s","spanId":"r1","name":"root",
                     "startTimeUnixNano":"100","endTimeUnixNano":"200"}]}]}]}
                """.formatted(traceId);
        JsonNode resp = machinePost("/v1/otlp/v1/traces", MACHINE_TOKEN, body);
        assertThat(resp.get("data").get("storedCount").asInt()).isEqualTo(1);
        assertThat(resp.get("data").get("droppedCount").asInt()).isZero();

        TraceSpan span = spansOf(traceId).get(0);
        assertThat(span.getAgentId()).isEqualTo(agentId);
        assertThat(span.getEnv()).isEqualTo("UAT");
        assertThat(span.getInvocationId()).isEqualTo(record.getId());
    }

    @Test
    void machineChannelRejectsMissingOrWrongToken() throws Exception {
        String body = "{\"resourceSpans\":[]}";
        // 无 token
        RawResp noToken = machinePostRaw("/v1/otlp/v1/traces", null, body);
        assertThat(noToken.status()).isEqualTo(401);
        assertThat(objectMapper.readTree(noToken.body()).get("code").asInt()).isEqualTo(40101);
        // 错 token
        RawResp wrongToken = machinePostRaw("/v1/otlp/v1/traces", "wrong-token", body);
        assertThat(wrongToken.status()).isEqualTo(401);
        // 告警 webhook 同样校验
        RawResp alertNoToken = machinePostRaw("/v1/alerts/webhook", null,
                "{\"title\":\"x\"}");
        assertThat(alertNoToken.status()).isEqualTo(401);
    }

    // ---------- 观测查询 ----------

    @Test
    void traceQueryAndDetailWithinProject() throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        String traceId = "t-q-" + SEQ.incrementAndGet();
        pushTwoSpans(agentId, traceId);

        // traces/query 按 traceId
        JsonNode queried = callApi("/v1/api/observability/traces/query", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("traceId", traceId));
        assertThat(queried.get("code").asInt()).as(String.valueOf(queried)).isEqualTo(0);
        assertThat(queried.get("data")).hasSize(2);

        // traces/query 按 agentId + 时间窗
        JsonNode byAgent = callApi("/v1/api/observability/traces/query", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId)
                        .put("createdFrom", java.time.LocalDateTime.now().minusHours(1).toString()));
        assertThat(byAgent.get("data")).hasSize(2);

        // traces/{traceId} 链路还原：按 startTime 排序，根 span 在前
        JsonNode detail = callApi("/v1/api/observability/traces/" + traceId, OWNER,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(detail.get("code").asInt()).as(String.valueOf(detail)).isEqualTo(0);
        assertThat(detail.get("data")).hasSize(2);
        assertThat(detail.get("data").get(0).get("parentSpanId").isNull()).isTrue();
        assertThat(detail.get("data").get(1).get("parentSpanId").asText()).isEqualTo("root");

        // 指定其他项目的 agentId：按项目隔离返回空
        long otherProject = createProjectQuietly();
        long otherAgent = createAgentQuietly(otherProject);
        JsonNode crossAgent = callApi("/v1/api/observability/traces/query", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", otherAgent));
        assertThat(crossAgent.get("data")).isEmpty();
    }

    @Test
    void crossProjectTraceAccessDenied() throws Exception {
        long projectA = createProject();
        long agentA = createAgent(projectA);
        String traceId = "t-x-" + SEQ.incrementAndGet();
        pushTwoSpans(agentA, traceId);

        long projectB = createProjectQuietly();
        addMember(projectB, OPERATOR_B, "OPERATOR");

        // 非项目成员：切面 obs:read 直接拒绝
        assertThat(callApi("/v1/api/observability/traces/query", OUTSIDER,
                objectMapper.createObjectNode().put("projectId", projectA).put("traceId", traceId))
                .get("code").asInt()).isEqualTo(40301);

        // 项目 B 的 OPERATOR（有 obs:read@B）查 A 的链路：数据不属于本项目 → 403
        assertThat(callApi("/v1/api/observability/traces/" + traceId, OPERATOR_B,
                objectMapper.createObjectNode().put("projectId", projectB))
                .get("code").asInt()).isEqualTo(40301);

        // 不存在的链路 → 404
        assertThat(callApi("/v1/api/observability/traces/no-such-trace", OWNER,
                objectMapper.createObjectNode().put("projectId", projectA))
                .get("code").asInt()).isEqualTo(40401);
    }

    @Test
    void auditQueryMasksDetailAsFallback() throws Exception {
        long projectId = createProject();
        String resourceId = "obs-test-" + SEQ.incrementAndGet();
        auditService.record("observability", "test.mask", "obs-test", resourceId,
                Map.of("token", "abc123", "plain", "ok"));

        JsonNode resp = callApi("/v1/api/observability/audit/query", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId)
                        .put("module", "observability").put("resourceType", "obs-test")
                        .put("resourceId", resourceId));
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        assertThat(resp.get("data")).hasSize(1);
        String detail = resp.get("data").get(0).get("detail").asText();
        assertThat(detail).contains("\"token\":\"***\"").contains("\"plain\":\"ok\"")
                .doesNotContain("abc123");

        // 非成员查审计 → 403
        assertThat(callApi("/v1/api/observability/audit/query", OUTSIDER,
                objectMapper.createObjectNode().put("projectId", projectId))
                .get("code").asInt()).isEqualTo(40301);
    }

    // ---------- 告警 ----------

    @Test
    void alertWebhookHandleFlow() throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        addMember(projectId, OPERATOR, "OPERATOR");
        addMember(projectId, DEVELOPER, "DEVELOPER");
        String alertKey = "k-" + SEQ.incrementAndGet();

        JsonNode pushed = machinePost("/v1/alerts/webhook", MACHINE_TOKEN, """
                {"source":"prom","alertKey":"%s","agentId":%d,"level":"CRITICAL","title":"RT 过高",
                 "detail":{"msg":"slow","secretKey":"s3"}}
                """.formatted(alertKey, agentId));
        assertThat(pushed.get("code").asInt()).as(String.valueOf(pushed)).isEqualTo(0);
        long alertId = pushed.get("data").get("id").asLong();
        assertThat(pushed.get("data").get("status").asText()).isEqualTo("FIRING");
        // detail 写入前脱敏
        assertThat(pushed.get("data").get("detail").asText()).contains("\"secretKey\":\"***\"")
                .doesNotContain("s3");

        // 平台级告警（agentId 为空）同样可见
        machinePost("/v1/alerts/webhook", MACHINE_TOKEN,
                "{\"source\":\"prom\",\"alertKey\":\"%s\",\"level\":\"WARN\",\"title\":\"磁盘\"}"
                        .formatted("k-plat-" + SEQ.incrementAndGet()));

        JsonNode list = callApi("/v1/api/observability/alerts/list", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(list.get("code").asInt()).isEqualTo(0);
        assertThat(list.get("data").size()).isGreaterThanOrEqualTo(2);

        // DEVELOPER 无 obs:alert → handle 拒绝
        assertThat(callApi("/v1/api/observability/alerts/handle", DEVELOPER,
                objectMapper.createObjectNode().put("projectId", projectId).put("id", alertId)
                        .put("note", "处理中")).get("code").asInt()).isEqualTo(40301);

        // OPERATOR 登记处置 → HANDLED
        JsonNode handled = callApi("/v1/api/observability/alerts/handle", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId).put("id", alertId)
                        .put("note", "已扩容处理"));
        assertThat(handled.get("code").asInt()).as(String.valueOf(handled)).isEqualTo(0);
        assertThat(handled.get("data").get("status").asText()).isEqualTo("HANDLED");
        assertThat(handled.get("data").get("handledBy").asText()).isEqualTo(OPERATOR);
        assertThat(handled.get("data").get("handleNote").asText()).isEqualTo("已扩容处理");

        // 重复 handle → 40902
        assertThat(callApi("/v1/api/observability/alerts/handle", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId).put("id", alertId)
                        .put("note", "again")).get("code").asInt()).isEqualTo(40902);

        // 处置落审计（module=observability）
        List<AuditEvent> audits = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "observability")
                .eq(AuditEvent::getAction, "alert.handle")
                .eq(AuditEvent::getResourceId, String.valueOf(alertId)));
        assertThat(audits).hasSize(1);
    }

    // ---------- 处置动作 ----------

    @Test
    void disableRouteDrainsGovernanceRoute() throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        addMember(projectId, OPERATOR, "OPERATOR");
        addMember(projectId, DEVELOPER, "DEVELOPER");

        ServiceRoute route = new ServiceRoute();
        route.setAgentId(agentId);
        route.setEnv("UAT");
        route.setAgentVersion("1.0.0");
        route.setDeploymentId(9_000_000L + SEQ.incrementAndGet());
        route.setRouteRevision(1);
        route.setStatus(RouteStatus.ACTIVE);
        routeMapper.insert(route);

        // DEVELOPER 无 obs:action → 拒绝，路由仍 ACTIVE
        assertThat(callApi("/v1/api/observability/actions/disable-route", DEVELOPER,
                objectMapper.createObjectNode().put("projectId", projectId).put("routeId", route.getId())
                        .put("reason", "降级")).get("code").asInt()).isEqualTo(40301);
        assertThat(routeMapper.selectById(route.getId()).getStatus()).isEqualTo(RouteStatus.ACTIVE);

        // OPERATOR 处置 → 跨模块验证：governance 侧路由确为 DRAINED
        JsonNode resp = callApi("/v1/api/observability/actions/disable-route", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId).put("routeId", route.getId())
                        .put("reason", "异常调用激增，停流量"));
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        assertThat(resp.get("data").get("status").asText()).isEqualTo("DRAINED");
        assertThat(routeMapper.selectById(route.getId()).getStatus()).isEqualTo(RouteStatus.DRAINED);

        // 双侧审计：observability 处置动作 + governance route.drain
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "observability")
                .eq(AuditEvent::getAction, "obs.action.disable-route")
                .eq(AuditEvent::getResourceId, String.valueOf(route.getId())))).isEqualTo(1);
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "governance")
                .eq(AuditEvent::getAction, "route.drain")
                .eq(AuditEvent::getResourceId, String.valueOf(route.getId())))).isEqualTo(1);
    }

    @Test
    void revokeCallerTakesEffectOnRuntimeAuth() throws Exception {
        long projectId = createProject();
        long agentId = createAgent(projectId);
        String agentCode = lastAgentCode;
        addMember(projectId, OPERATOR, "OPERATOR");
        String callerCode = "obs-caller-" + SEQ.incrementAndGet();

        // 授权 AGENT 调用策略 → agent-auth 允许
        JsonNode granted = callApi("/v1/api/caller-policies/grant", OWNER,
                objectMapper.createObjectNode().put("projectId", projectId).put("agentId", agentId)
                        .put("env", "UAT").put("callerType", "AGENT").put("callerId", callerCode));
        assertThat(granted.get("code").asInt()).as(String.valueOf(granted)).isEqualTo(0);
        JsonNode before = runtimePost("/v1/agent-auth/check", objectMapper.createObjectNode()
                .put("callerAgentCode", callerCode).put("calleeAgentCode", agentCode).put("env", "UAT"));
        assertThat(before.get("data").get("allowed").asBoolean()).isTrue();

        long policyId = granted.get("data").get("id").asLong();
        JsonNode revoked = callApi("/v1/api/observability/actions/revoke-caller", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId).put("policyId", policyId)
                        .put("reason", "调用方异常"));
        assertThat(revoked.get("code").asInt()).as(String.valueOf(revoked)).isEqualTo(0);
        assertThat(revoked.get("data").get("status").asText()).isEqualTo("REVOKED");

        // 吊销生效：agent-auth 拒绝；策略表确为 REVOKED
        JsonNode after = runtimePost("/v1/agent-auth/check", objectMapper.createObjectNode()
                .put("callerAgentCode", callerCode).put("calleeAgentCode", agentCode).put("env", "UAT"));
        assertThat(after.get("data").get("allowed").asBoolean()).isFalse();
        CallerPolicy policy = callerPolicyMapper.selectById(policyId);
        assertThat(policy.getStatus()).isEqualTo(PolicyStatus.REVOKED);

        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "observability")
                .eq(AuditEvent::getAction, "obs.action.revoke-caller")
                .eq(AuditEvent::getResourceId, String.valueOf(policyId)))).isEqualTo(1);
    }

    // ---------- 组件运维 ----------

    @Test
    void componentCrudAndHealthAggregation() throws Exception {
        long projectId = createProject();
        addMember(projectId, OPERATOR, "OPERATOR");
        addMember(projectId, DEVELOPER, "DEVELOPER");
        String prefix = "c" + SEQ.incrementAndGet() + "-";

        // DEVELOPER 无 obs:component → 拒绝
        assertThat(callApi("/v1/api/observability/components/register", DEVELOPER,
                componentBody(projectId, prefix + "db", "DATABASE", null))
                .get("code").asInt()).isEqualTo(40301);

        JsonNode up = callApi("/v1/api/observability/components/register", OPERATOR,
                componentBody(projectId, prefix + "up", "SERVICE",
                        "http://127.0.0.1:" + healthPort + "/health"));
        assertThat(up.get("code").asInt()).as(String.valueOf(up)).isEqualTo(0);
        long upId = up.get("data").get("id").asLong();
        assertThat(callApi("/v1/api/observability/components/register", OPERATOR,
                componentBody(projectId, prefix + "down", "EXTERNAL", "http://127.0.0.1:1/down"))
                .get("code").asInt()).isEqualTo(0);
        assertThat(callApi("/v1/api/observability/components/register", OPERATOR,
                componentBody(projectId, prefix + "noep", "DATABASE", null))
                .get("code").asInt()).isEqualTo(0);

        // code 重复 → 40901
        assertThat(callApi("/v1/api/observability/components/register", OPERATOR,
                componentBody(projectId, prefix + "up", "SERVICE", null))
                .get("code").asInt()).isEqualTo(40901);

        // update + list
        assertThat(callApi("/v1/api/observability/components/update", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId).put("id", upId)
                        .put("ownerTeam", "平台组").put("critical", true))
                .get("code").asInt()).isEqualTo(0);
        JsonNode list = callApi("/v1/api/observability/components/list", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(list.get("data").size()).isGreaterThanOrEqualTo(3);

        // health 聚合：UP / DOWN（不可达）/ UNKNOWN（无 endpoint）
        JsonNode health = callApi("/v1/api/observability/components/health", OPERATOR,
                objectMapper.createObjectNode().put("projectId", projectId));
        assertThat(health.get("code").asInt()).as(String.valueOf(health)).isEqualTo(0);
        JsonNode data = health.get("data");
        assertThat(data.get("total").asInt()).isGreaterThanOrEqualTo(3);
        Map<String, String> statusByCode = new java.util.HashMap<>();
        data.get("components").forEach(c -> statusByCode.put(c.get("code").asText(),
                c.get("status").asText()));
        assertThat(statusByCode.get(prefix + "up")).isEqualTo("UP");
        assertThat(statusByCode.get(prefix + "down")).isEqualTo("DOWN");
        assertThat(statusByCode.get(prefix + "noep")).isEqualTo("UNKNOWN");
        assertThat(data.get("up").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("down").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("unknown").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("total").asInt())
                .isEqualTo(data.get("up").asInt() + data.get("down").asInt() + data.get("unknown").asInt());

        // 管理面写操作落审计（module=observability）
        assertThat(auditEventMapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getModule, "observability")
                .eq(AuditEvent::getAction, "component.register"))).isGreaterThanOrEqualTo(3);
    }

    // ---------- fixtures / helpers ----------

    private String lastAgentCode;

    private long createProject() throws Exception {
        long projectId = createProjectQuietly();
        return projectId;
    }

    private long createProjectQuietly() {
        try {
            JsonNode response = callApi("/v1/api/projects/create", OWNER, objectMapper.createObjectNode()
                    .put("code", "obs-p" + SEQ.incrementAndGet()).put("name", "观测测试项目"));
            assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
            return response.get("data").get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long createAgent(long projectId) throws Exception {
        return createAgentQuietly(projectId);
    }

    private long createAgentQuietly(long projectId) {
        try {
            lastAgentCode = "obs-ag-" + SEQ.incrementAndGet();
            JsonNode response = callApi("/v1/api/agents/create", OWNER, objectMapper.createObjectNode()
                    .put("code", lastAgentCode).put("name", "观测测试 Agent")
                    .put("projectId", projectId).put("accessMode", "ADAPTED"));
            assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
            return response.get("data").get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void addMember(long projectId, String subjectId, String role) throws Exception {
        JsonNode response = callApi("/v1/api/projects/" + projectId + "/members/add", OWNER,
                objectMapper.createObjectNode().put("subjectType", "USER")
                        .put("subjectId", subjectId).put("role", role));
        assertThat(response.get("code").asInt()).as(String.valueOf(response)).isEqualTo(0);
    }

    /** 推送一条 root + 一条 child span（resource attribute agent.id 映射） */
    private void pushTwoSpans(long agentId, String traceId) throws Exception {
        String body = """
                {"resourceSpans":[{"resource":{"attributes":[
                    {"key":"agent.id","value":{"stringValue":"%d"}}]},
                 "scopeSpans":[{"spans":[
                    {"traceId":"%s","spanId":"root","name":"agent.run",
                     "startTimeUnixNano":"1000","endTimeUnixNano":"2000"},
                    {"traceId":"%s","spanId":"child","parentSpanId":"root","name":"llm.call",
                     "startTimeUnixNano":"1200","endTimeUnixNano":"1800",
                     "attributes":[{"key":"agentops.span.kind","value":{"stringValue":"LLM"}}]}
                 ]}]}]}
                """.formatted(agentId, traceId, traceId);
        JsonNode resp = machinePost("/v1/otlp/v1/traces", MACHINE_TOKEN, body);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        assertThat(resp.get("data").get("storedCount").asInt()).isEqualTo(2);
    }

    private ObjectNode componentBody(long projectId, String code, String type, String healthEndpoint) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("projectId", projectId).put("code", code).put("name", "组件-" + code)
                .put("type", type);
        if (healthEndpoint != null) {
            body.put("healthEndpoint", healthEndpoint);
        }
        return body;
    }

    private List<TraceSpan> spansOf(String traceId) {
        return traceSpanMapper.selectList(new LambdaQueryWrapper<TraceSpan>()
                .eq(TraceSpan::getTraceId, traceId).orderByAsc(TraceSpan::getId));
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

    private JsonNode machinePost(String path, String token, String body) throws Exception {
        return objectMapper.readTree(machinePostRaw(path, token, body).body());
    }

    private record RawResp(int status, String body) {
    }

    /** 用 java.net.http.HttpClient 发机器通道请求（RestTemplate 遇 401 会触发流式重试报错） */
    private RawResp machinePostRaw(String path, String token, String body) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("X-Platform-Token", token);
        }
        java.net.http.HttpResponse<String> resp = httpClient.send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new RawResp(resp.statusCode(), resp.body());
    }

    private JsonNode runtimePost(String path, ObjectNode body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = restTemplate.postForEntity(path,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return objectMapper.readTree(resp.getBody());
    }
}
