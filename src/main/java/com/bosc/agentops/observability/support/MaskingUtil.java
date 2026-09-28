package com.bosc.agentops.observability.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 敏感字段脱敏：键名不区分大小写，前缀命中 password|secret|token|apikey|authorization 时值替换为 ***。
 * 写入（OTLP attrs、告警 detail）与查询（traces/audit 返回兜底）两层都使用。
 */
@Component
public class MaskingUtil {

    public static final String MASK = "***";

    private static final Logger log = LoggerFactory.getLogger(MaskingUtil.class);

    private static final List<String> SENSITIVE_PREFIXES =
            List.of("password", "secret", "token", "apikey", "authorization");

    private final ObjectMapper objectMapper;

    public MaskingUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String prefix : SENSITIVE_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 脱敏 JSON 文本；非合法 JSON 时原样返回（不掩盖存储问题） */
    public String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode masked = maskNode(objectMapper.readTree(json));
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            log.warn("脱敏解析失败，原样返回: {}", e.getMessage());
            return json;
        }
    }

    /** 递归脱敏：敏感键直接替换值，不递归其内容 */
    public JsonNode maskNode(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).objectNode();
            node.fields().forEachRemaining(entry -> {
                if (isSensitiveKey(entry.getKey())) {
                    copy.set(entry.getKey(), TextNode.valueOf(MASK));
                } else {
                    copy.set(entry.getKey(), maskNode(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = ((ArrayNode) node).arrayNode();
            for (JsonNode item : node) {
                copy.add(maskNode(item));
            }
            return copy;
        }
        return node;
    }

    /** Map 形式脱敏（OTLP attributes 聚合成 Map 后使用） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> maskMap(Map<String, Object> attrs) {
        if (attrs == null) {
            return null;
        }
        JsonNode masked = maskNode(objectMapper.valueToTree(attrs));
        return objectMapper.convertValue(masked, Map.class);
    }
}
