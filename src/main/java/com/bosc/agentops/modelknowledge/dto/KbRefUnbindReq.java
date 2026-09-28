package com.bosc.agentops.modelknowledge.dto;

import jakarta.validation.constraints.NotNull;

public class KbRefUnbindReq {

    @NotNull
    private Long refId;

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }
}
