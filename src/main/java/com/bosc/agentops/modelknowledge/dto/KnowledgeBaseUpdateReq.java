package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KnowledgeBaseUpdateReq {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String endpoint;

    /** 连接配置 JSON，不含明文密钥 */
    private JsonNode connectionConfig;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public JsonNode getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(JsonNode connectionConfig) {
        this.connectionConfig = connectionConfig;
    }
}
