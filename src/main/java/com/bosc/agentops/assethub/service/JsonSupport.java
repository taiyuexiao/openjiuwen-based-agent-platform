package com.bosc.agentops.assethub.service;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 模块内 JSON 文本字段（definition）的序列化辅助。 */
final class JsonSupport {

    private JsonSupport() {
    }

    /** JsonNode → 库存 JSON 文本；null 节点存 null */
    static String toJson(ObjectMapper objectMapper, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "JSON 序列化失败: " + e.getMessage());
        }
    }

    /** 库存 JSON 文本 → JsonNode；空返回 null */
    static JsonNode toNode(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 字段解析失败: " + e.getMessage());
        }
    }
}
