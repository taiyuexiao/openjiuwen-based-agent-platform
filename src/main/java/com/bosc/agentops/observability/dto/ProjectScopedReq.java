package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 仅需项目 scope 的请求（如 components/health）。
 */
public class ProjectScopedReq {

    @NotNull
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
