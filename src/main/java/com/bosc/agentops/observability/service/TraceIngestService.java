package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.observability.dto.OtlpIngestResp;
import com.bosc.agentops.observability.entity.SpanKind;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.mapper.TraceSpanMapper;
import com.bosc.agentops.observability.support.MaskingUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * OTLP/HTTP JSON 接收：解析 resourceSpans→spans 落库。
 * Agent 映射：resource attribute agent.id 优先，其次 service.name 匹配 agent code；
 * 根 span 仍无法映射时按 traceId 查 07 的 InvocationRecord 补记（含 invocationId 关联）；
 * 最终无法映射的 span 丢弃并计入 droppedCount。
 */
@Service
public class TraceIngestService {

    private static final Logger log = LoggerFactory.getLogger(TraceIngestService.class);

    static final String ATTR_AGENT_ID = "agent.id";
    static final String ATTR_SERVICE_NAME = "service.name";
    static final String ATTR_DEPLOY_ENV = "deployment.environment";
    static final String ATTR_SPAN_KIND = "agentops.span.kind";
    static final String DEFAULT_ENV = "DEV";

    private final TraceSpanMapper traceSpanMapper;
    private final AgentMapper agentMapper;
    private final InvocationRecordMapper invocationRecordMapper;
    private final MaskingUtil maskingUtil;
    private final ObjectMapper objectMapper;

    public TraceIngestService(TraceSpanMapper traceSpanMapper, AgentMapper agentMapper,
                              InvocationRecordMapper invocationRecordMapper,
                              MaskingUtil maskingUtil, ObjectMapper objectMapper) {
        this.traceSpanMapper = traceSpanMapper;
        this.agentMapper = agentMapper;
        this.invocationRecordMapper = invocationRecordMapper;
        this.maskingUtil = maskingUtil;
        this.objectMapper = objectMapper;
    }

    public OtlpIngestResp ingest(JsonNode root) {
        int received = 0;
        int stored = 0;
        int dropped = 0;
        for (JsonNode resourceSpans : root.path("resourceSpans")) {
            Map<String, Object> resourceAttrs = attributesToMap(resourceSpans.path("resource").path("attributes"));
            Long agentId = resolveAgentId(resourceAttrs);
            String env = resolveEnv(resourceAttrs);
            for (JsonNode scopeSpans : resourceSpans.path("scopeSpans")) {
                for (JsonNode spanNode : scopeSpans.path("spans")) {
                    received++;
                    TraceSpan span = mapSpan(spanNode, agentId, env);
                    if (span == null) {
                        dropped++;
                        continue;
                    }
                    if (span.getParentSpanId() == null) {
                        correlateInvocation(span);
                    }
                    if (span.getAgentId() == null) {
                        dropped++;
                        continue;
                    }
                    traceSpanMapper.insert(span);
                    stored++;
                }
            }
        }
        if (dropped > 0) {
            log.info("OTLP 接收完成: received={}, stored={}, dropped={}", received, stored, dropped);
        }
        return new OtlpIngestResp(received, stored, dropped);
    }

    /** resource attribute agent.id（需真实存在）优先，其次 service.name 匹配 agent code */
    private Long resolveAgentId(Map<String, Object> resourceAttrs) {
        Object agentIdValue = resourceAttrs.get(ATTR_AGENT_ID);
        if (agentIdValue != null) {
            try {
                long id = Long.parseLong(agentIdValue.toString());
                if (agentMapper.selectById(id) != null) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // 落到 service.name 匹配
            }
        }
        Object serviceName = resourceAttrs.get(ATTR_SERVICE_NAME);
        if (serviceName != null && !serviceName.toString().isBlank()) {
            Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getCode, serviceName.toString()).last("LIMIT 1"));
            if (agent != null) {
                return agent.getId();
            }
        }
        return null;
    }

    private String resolveEnv(Map<String, Object> resourceAttrs) {
        Object env = resourceAttrs.get(ATTR_DEPLOY_ENV);
        if (env == null || env.toString().isBlank()) {
            return DEFAULT_ENV;
        }
        return env.toString().toUpperCase(Locale.ROOT);
    }

    /** 根 span：存在同 traceId 的 InvocationRecord 时补记关联（agentId/env 兜底 + invocationId） */
    private void correlateInvocation(TraceSpan span) {
        InvocationRecord record = invocationRecordMapper.selectOne(new LambdaQueryWrapper<InvocationRecord>()
                .eq(InvocationRecord::getTraceId, span.getTraceId()).last("LIMIT 1"));
        if (record == null) {
            return;
        }
        span.setInvocationId(record.getId());
        if (span.getAgentId() == null && record.getAgentId() != null) {
            span.setAgentId(record.getAgentId());
            if (record.getEnv() != null) {
                span.setEnv(record.getEnv());
            }
        }
    }

    private TraceSpan mapSpan(JsonNode spanNode, Long agentId, String env) {
        String traceId = textOrNull(spanNode, "traceId");
        String spanId = textOrNull(spanNode, "spanId");
        if (traceId == null || spanId == null) {
            return null;
        }
        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(spanId);
        span.setParentSpanId(textOrNull(spanNode, "parentSpanId"));
        span.setSpanName(textOrNull(spanNode, "name"));
        span.setStartTime(longOrNull(spanNode, "startTimeUnixNano"));
        span.setEndTime(longOrNull(spanNode, "endTimeUnixNano"));
        span.setStatus(mapStatus(spanNode.path("status")));
        Map<String, Object> attrs = attributesToMap(spanNode.path("attributes"));
        span.setSpanKind(mapSpanKind(attrs, span.getParentSpanId() == null));
        span.setAgentId(agentId);
        span.setEnv(env);
        try {
            span.setAttrs(objectMapper.writeValueAsString(maskingUtil.maskMap(attrs)));
        } catch (Exception e) {
            span.setAttrs(null);
        }
        return span;
    }

    private SpanKind mapSpanKind(Map<String, Object> attrs, boolean root) {
        Object hint = attrs.get(ATTR_SPAN_KIND);
        if (hint != null) {
            try {
                return SpanKind.valueOf(hint.toString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 落到默认推断
            }
        }
        return root ? SpanKind.AGENT : SpanKind.OTHER;
    }

    /** OTLP status.code：1/OK → OK；2/ERROR → ERROR；其余 UNSET */
    private String mapStatus(JsonNode statusNode) {
        JsonNode code = statusNode.path("code");
        if (code.isNumber()) {
            return switch (code.asInt()) {
                case 1 -> "OK";
                case 2 -> "ERROR";
                default -> "UNSET";
            };
        }
        String text = code.asText("").toUpperCase(Locale.ROOT);
        if (text.contains("ERROR")) {
            return "ERROR";
        }
        if (text.contains("OK")) {
            return "OK";
        }
        return "UNSET";
    }

    /** OTLP KeyValue 数组 → Map（标量值；复合值取紧凑 JSON 字符串） */
    private Map<String, Object> attributesToMap(JsonNode attributes) {
        Map<String, Object> result = new HashMap<>();
        if (!attributes.isArray()) {
            return result;
        }
        for (JsonNode kv : attributes) {
            String key = kv.path("key").asText(null);
            if (key == null) {
                continue;
            }
            result.put(key, valueToScalar(kv.path("value")));
        }
        return result;
    }

    private Object valueToScalar(JsonNode value) {
        if (value.has("stringValue")) {
            return value.get("stringValue").asText();
        }
        if (value.has("intValue")) {
            return value.get("intValue").asLong();
        }
        if (value.has("doubleValue")) {
            return value.get("doubleValue").asDouble();
        }
        if (value.has("boolValue")) {
            return value.get("boolValue").asBoolean();
        }
        return value.isMissingNode() || value.isNull() ? null : value.toString();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.isNumber() ? value.asLong() : Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
