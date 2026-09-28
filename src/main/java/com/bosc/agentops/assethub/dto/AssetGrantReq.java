package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotNull;

/** 跨项目授权/回收入参：projectId 为归属项目（操作面），toProjectId 为被授权项目。 */
public class AssetGrantReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long toProjectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getToProjectId() {
        return toProjectId;
    }

    public void setToProjectId(Long toProjectId) {
        this.toProjectId = toProjectId;
    }
}
