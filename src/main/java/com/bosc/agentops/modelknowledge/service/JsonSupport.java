package com.bosc.agentops.modelknowledge.service;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块内 JSON 文本字段（capabilities/defaultParams/paramPolicy/grantScope/connectionConfig）的序列化辅助。
 */
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

    /** 库存 JSON 文本 → Map；空返回空 Map */
    static Map<String, Object> toMap(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 字段解析失败: " + e.getMessage());
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
