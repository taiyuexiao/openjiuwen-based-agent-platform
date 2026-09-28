package com.bosc.agentops.assethub.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OpenAPI paths 解析出的工具定义草案。name=operationId 或 method+path；
 * parameters / requestBody 为原文 schema 节点，原样保留。
 */
public class ToolDraft {

    private String name;
    private String description;
    private String method;
    private String path;
    private JsonNode parameters;
    private JsonNode requestBody;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }

    public JsonNode getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(JsonNode requestBody) {
        this.requestBody = requestBody;
    }
}
