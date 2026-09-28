package com.bosc.agentops.delivery.service;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模块内 JSON 文本字段（baseResource/allowedAgentLevels/gateResult）的序列化辅助。
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /** JsonNode → 库存 JSON 文本；null 节点存 null */
    public static String toJson(ObjectMapper objectMapper, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "JSON 序列化失败: " + e.getMessage());
        }
    }

    /** 对象 → JSON 文本 */
    public static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败: " + e.getMessage());
        }
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
