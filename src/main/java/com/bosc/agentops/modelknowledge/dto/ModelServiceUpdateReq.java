package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ModelServiceUpdateReq {

    @NotNull
    private Long id;

    @Size(max = 128)
    private String displayName;

    /** 能力 JSON */
    private JsonNode capabilities;

    /** 默认调用参数 JSON */
    private JsonNode defaultParams;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
