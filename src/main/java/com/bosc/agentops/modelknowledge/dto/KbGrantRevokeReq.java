package com.bosc.agentops.modelknowledge.dto;

import jakarta.validation.constraints.NotNull;

public class KbGrantRevokeReq {

    @NotNull
    private Long kbId;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }
}
