package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ModelServiceCreateReq {

    @NotNull
    private Long providerId;

    @NotBlank
    @Size(max = 128)
    private String modelCode;

    @Size(max = 128)
    private String displayName;

    /** 能力 JSON，如 {"chat":true,"embedding":true} */
    private JsonNode capabilities;

    /** 默认调用参数 JSON */
    private JsonNode defaultParams;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public JsonNode getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(JsonNode capabilities) {
        this.capabilities = capabilities;
    }

    public JsonNode getDefaultParams() {
        return defaultParams;
    }

    public void setDefaultParams(JsonNode defaultParams) {
        this.defaultParams = defaultParams;
    }
}
