package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class KnowledgeBaseCreateReq {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "仅允许字母、数字、中划线")
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    /** 行内知识库系统标识 */
    @NotBlank
    @Size(max = 64)
    private String kbType;

    @Size(max = 512)
    private String endpoint;

    /** 连接配置 JSON，不含明文密钥 */
    private JsonNode connectionConfig;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKbType() {
        return kbType;
    }

    public void setKbType(String kbType) {
        this.kbType = kbType;
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
