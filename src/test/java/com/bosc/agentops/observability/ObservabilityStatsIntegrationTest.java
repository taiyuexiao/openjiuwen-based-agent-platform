package com.bosc.agentops.observability;

import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.entity.InvocationStatus;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.observability.entity.AlertEvent;
import com.bosc.agentops.observability.entity.AlertStatus;
import com.bosc.agentops.observability.entity.SpanKind;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.mapper.AlertEventMapper;
import com.bosc.agentops.observability.mapper.TraceSpanMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 观测统计聚合端到端测试：项目级聚合数字正确（含窗口过滤与跨项目隔离）、
 * 平台级聚合、非成员 40301、空项目零值结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityStatsIntegrationTest {

    private static final String OWNER = "u1001";
    private static final String OUTSIDER = "u1006";

    private static final AtomicLong CODE_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InvocationRecordMapper invocationRecordMapper;
    @Autowired
    private TraceSpanMapper traceSpanMapper;
    @Autowired
    private AlertEventMapper alertEventMapper;

    @Test
    void projectOverviewAggregatesCorrectly() throws Exception {
        long projectId = createProject(OWNER);
        long otherProjectId = createProject(OWNER);
        long agent1 = createAgent(OWNER, projectId, "st-a1-");
        long agent2 = createAgent(OWNER, projectId, "st-a2-");
        long otherAgent = createAgent(OWNER, otherProjectId, "st-other-");

        // 项目内：agent1 两条 SUCCESS(100/200ms) + 一条 FAILED(300ms)，agent2 一条 SUCCESS(500ms)
        insertRecord(agent1, InvocationStatus.SUCCESS, 100L, LocalDateTime.now());
        insertRecord(agent1, InvocationStatus.SUCCESS, 200L, LocalDateTime.now());
        insertRecord(agent1, InvocationStatus.FAILED, 300L, LocalDateTime.now());
        insertRecord(agent2, InvocationStatus.SUCCESS, 500L, LocalDateTime.now());
        // 窗口外（10 天前）与跨项目数据不应计入
        insertRecord(agent1, InvocationStatus.SUCCESS, 100L, LocalDateTime.now().minusDays(10));
        insertRecord(otherAgent, InvocationStatus.SUCCESS, 100L, LocalDateTime.now());

        insertSpan(agent1, SpanKind.LLM);
        insertSpan(agent1, SpanKind.LLM);
        insertSpan(agent1, SpanKind.TOOL);
        insertSpan(otherAgent, SpanKind.LLM);

        insertAlert(agent1, AlertStatus.FIRING);
        insertAlert(agent1, AlertStatus.FIRING);
        insertAlert(agent1, AlertStatus.HANDLED);
        insertAlert(otherAgent, AlertStatus.FIRING);

        JsonNode resp = overview(OWNER, projectId);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        JsonNode data = resp.get("data");
        assertThat(data.get("scope").asText()).isEqualTo("PROJECT");
        assertThat(data.get("projectId").asLong()).isEqualTo(projectId);
        assertThat(data.get("totalCalls").asLong()).isEqualTo(4);
        assertThat(data.get("successRate").asDouble()).isCloseTo(0.75, offset(1e-9));
        assertThat(data.get("avgLatencyMs").asDouble()).isCloseTo(275.0, offset(1e-9));
        assertThat(data.get("alertOpenCount").asLong()).isEqualTo(2);

        JsonNode trend = data.get("dailyTrend");
        assertThat(trend).hasSize(7);
        JsonNode today = trend.get(6);
        assertThat(today.get("date").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(today.get("total").asLong()).isEqualTo(4);
        assertThat(today.get("success").asLong()).isEqualTo(3);
        assertThat(today.get("failed").asLong()).isEqualTo(1);
        for (int i = 0; i < 6; i++) {
            assertThat(trend.get(i).get("total").asLong())
                    .as("date=%s", trend.get(i).get("date").asText()).isZero();
        }

        JsonNode topAgents = data.get("topAgents");
        assertThat(topAgents).hasSize(2);
        assertThat(topAgents.get(0).get("agentId").asLong()).isEqualTo(agent1);
        assertThat(topAgents.get(0).get("calls").asLong()).isEqualTo(3);
        assertThat(topAgents.get(1).get("agentId").asLong()).isEqualTo(agent2);
        assertThat(topAgents.get(1).get("calls").asLong()).isEqualTo(1);

        JsonNode spanKindDist = data.get("spanKindDist");
        assertThat(spanKindDist.get("LLM").asLong()).isEqualTo(2);
        assertThat(spanKindDist.get("TOOL").asLong()).isEqualTo(1);
        assertThat(spanKindDist.has("AGENT")).isFalse();

        // 非项目成员查该项目 → 40301
        JsonNode outsiderResp = overview(OUTSIDER, projectId);
        assertThat(outsiderResp.get("code").asInt()).isEqualTo(40301);
    }

    @Test
    void platformOverviewAggregatesAcrossProjects() throws Exception {
        long projectId = createProject(OWNER);
        long agentId = createAgent(OWNER, projectId, "st-plat-");
        insertRecord(agentId, InvocationStatus.SUCCESS, 100L, LocalDateTime.now());
        insertSpan(agentId, SpanKind.WORKFLOW);
        insertAlert(agentId, AlertStatus.FIRING);

        JsonNode resp = overview(OUTSIDER, null);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        JsonNode data = resp.get("data");
        assertThat(data.get("scope").asText()).isEqualTo("PLATFORM");
        assertThat(data.get("projectId").isNull()).isTrue();
        // 平台级至少覆盖刚构造的数据（共享库上可能有其他测试数据，只断言下界与存在性）
        assertThat(data.get("totalCalls").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("spanKindDist").get("WORKFLOW").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("alertOpenCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("dailyTrend")).hasSize(7);
        assertThat(data.get("topAgents").isArray()).isTrue();
    }

    @Test
    void emptyProjectReturnsZeroStructure() throws Exception {
        long projectId = createProject(OWNER);
        JsonNode resp = overview(OWNER, projectId);
        assertThat(resp.get("code").asInt()).as(String.valueOf(resp)).isEqualTo(0);
        JsonNode data = resp.get("data");
        assertThat(data.get("totalCalls").asLong()).isZero();
        assertThat(data.get("successRate").asDouble()).isZero();
        assertThat(data.get("avgLatencyMs").asDouble()).isZero();
        assertThat(data.get("alertOpenCount").asLong()).isZero();
        assertThat(data.get("dailyTrend")).hasSize(7);
        for (JsonNode day : data.get("dailyTrend")) {
            assertThat(day.get("total").asLong()).isZero();
            assertThat(day.get("success").asLong()).isZero();
            assertThat(day.get("failed").asLong()).isZero();
        }
        assertThat(data.get("topAgents")).isEmpty();
        assertThat(data.get("spanKindDist").isEmpty()).isTrue();
    }

    // ---------- helpers ----------

    private JsonNode overview(String userId, Long projectId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (projectId != null) {
            body.put("projectId", projectId);
        }
        return callApi("/v1/api/observability/stats/overview", userId, body);
    }

    private void insertRecord(long agentId, InvocationStatus status, Long latencyMs, LocalDateTime createdAt) {
        InvocationRecord record = new InvocationRecord();
        record.setTraceId("st-t-" + CODE_SEQ.incrementAndGet());
        record.setAgentId(agentId);
        record.setEnv("DEV");
        record.setStatus(status);
        record.setLatencyMs(latencyMs);
        record.setCreatedAt(createdAt);
        invocationRecordMapper.insert(record);
    }

    private void insertSpan(long agentId, SpanKind spanKind) {
        TraceSpan span = new TraceSpan();
        long seq = CODE_SEQ.incrementAndGet();
        span.setTraceId("st-trace-" + seq);
        span.setSpanId("st-span-" + seq);
        span.setAgentId(agentId);
        span.setEnv("DEV");
        span.setSpanKind(spanKind);
        span.setStatus("OK");
        traceSpanMapper.insert(span);
    }

    private void insertAlert(long agentId, AlertStatus status) {
        AlertEvent alert = new AlertEvent();
        alert.setSource("test");
        alert.setAlertKey("st-alert-" + CODE_SEQ.incrementAndGet());
        alert.setAgentId(agentId);
        alert.setLevel("P2");
        alert.setTitle("统计测试告警");
        alert.setStatus(status);
        alertEventMapper.insert(alert);
    }

    private long createProject(String userId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", "st-p" + CODE_SEQ.incrementAndGet())
                .put("name", "统计测试项目");
        JsonNode response = callApi("/v1/api/projects/create", userId, body);
        assertThat(response.get("code").asInt()).isEqualTo(0);
        return response.get("data").get("id").asLong();
    }

    private long createAgent(String operator, long projectId, String codePrefix) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("code", codePrefix + CODE_SEQ.incrementAndGet())
                .put("name", "统计测试 Agent")
                .put("projectId", projectId);
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
