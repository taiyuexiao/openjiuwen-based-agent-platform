package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 部署实例列表过滤（可按 releaseId 过滤）。
 */
public class DeploymentListReq {

    @NotNull
    private Long projectId;

    private Long releaseId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }
}
