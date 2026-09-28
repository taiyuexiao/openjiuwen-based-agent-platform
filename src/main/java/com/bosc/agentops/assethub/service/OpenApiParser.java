package com.bosc.agentops.assethub.service;

import com.bosc.agentops.assethub.dto.ToolDraft;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI 3.x JSON 解析（Jackson 读 JSON 树，不引入重型解析库）。
 * 遍历 paths 生成工具定义草案：name=operationId 或 method+path；description 取 summary/description；
 * parameters（path 级与 operation 级合并）与 requestBody 的 schema 原样保留。
 */
final class OpenApiParser {

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private OpenApiParser() {
    }

    /** 解析并校验 OpenAPI 3.x JSON，返回工具草案列表 */
    static List<ToolDraft> parseTools(ObjectMapper objectMapper, String openApiJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(openApiJson);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "OpenAPI 文档不是合法 JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "OpenAPI 文档必须为 JSON 对象");
        }
        JsonNode openapi = root.get("openapi");
        if (openapi == null || !openapi.isTextual() || !openapi.asText().startsWith("3.")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅支持 OpenAPI 3.x（openapi 字段缺失或版本不符）");
        }
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "OpenAPI 文档缺少 paths 对象");
        }

        List<ToolDraft> tools = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathIt = paths.fields();
        while (pathIt.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIt.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            if (pathItem == null || !pathItem.isObject()) {
                continue;
            }
            JsonNode pathLevelParams = pathItem.get("parameters");
            for (String method : HTTP_METHODS) {
                JsonNode op = pathItem.get(method);
                if (op == null || !op.isObject()) {
                    continue;
                }
                ToolDraft draft = new ToolDraft();
                draft.setMethod(method.toUpperCase());
                draft.setPath(path);
                draft.setName(resolveName(op, method, path));
                draft.setDescription(resolveDescription(op));
                draft.setParameters(mergeParameters(objectMapper, pathLevelParams, op.get("parameters")));
                JsonNode requestBody = op.get("requestBody");
                draft.setRequestBody(requestBody == null || requestBody.isNull() ? null : requestBody);
                tools.add(draft);
            }
        }
        return tools;
    }

    /** name=operationId；缺省时回退 method+path（非字母数字折叠为下划线） */
    private static String resolveName(JsonNode op, String method, String path) {
        JsonNode operationId = op.get("operationId");
        if (operationId != null && operationId.isTextual() && !operationId.asText().isBlank()) {
            return operationId.asText();
        }
        String fallback = (method + "_" + path).replaceAll("[^A-Za-z0-9]+", "_");
        return fallback.replaceAll("^_+|_+$", "");
    }

    private static String resolveDescription(JsonNode op) {
        JsonNode summary = op.get("summary");
        if (summary != null && summary.isTextual() && !summary.asText().isBlank()) {
            return summary.asText();
        }
        JsonNode description = op.get("description");
        if (description != null && description.isTextual()) {
            return description.asText();
        }
        return null;
    }

    /** path 级 parameters 与 operation 级 parameters 合并（operation 级在后），schema 原样保留 */
    private static ArrayNode mergeParameters(ObjectMapper objectMapper, JsonNode pathLevel, JsonNode opLevel) {
        boolean pathIsArray = pathLevel != null && pathLevel.isArray();
        boolean opIsArray = opLevel != null && opLevel.isArray();
        if (!pathIsArray && !opIsArray) {
            return null;
        }
        ArrayNode merged = objectMapper.createArrayNode();
        if (pathIsArray) {
            pathLevel.forEach(merged::add);
        }
        if (opIsArray) {
            opLevel.forEach(merged::add);
        }
        return merged;
    }
}
