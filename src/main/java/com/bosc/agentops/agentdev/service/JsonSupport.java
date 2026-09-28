package com.bosc.agentops.agentdev.service;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模块内 JSON 文本字段（declaration/files）的序列化辅助。
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
}
