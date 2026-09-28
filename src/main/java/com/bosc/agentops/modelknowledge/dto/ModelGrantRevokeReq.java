package com.bosc.agentops.modelknowledge.dto;

import jakarta.validation.constraints.NotNull;

public class ModelGrantRevokeReq {

    @NotNull
    private Long modelServiceId;

    public Long getModelServiceId() {
        return modelServiceId;
    }

    public void setModelServiceId(Long modelServiceId) {
        this.modelServiceId = modelServiceId;
    }
}
