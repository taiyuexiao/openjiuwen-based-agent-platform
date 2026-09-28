package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotNull;

public class ReferenceRemoveReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long refId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }
}
