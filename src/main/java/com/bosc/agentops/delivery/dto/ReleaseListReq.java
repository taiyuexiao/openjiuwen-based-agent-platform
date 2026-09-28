package com.bosc.agentops.delivery.dto;

import com.bosc.agentops.delivery.entity.ReleaseStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 发布单列表过滤。
 */
public class ReleaseListReq {

    @NotNull
    private Long projectId;

    private Long agentId;

    private ReleaseStatus status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public void setStatus(ReleaseStatus status) {
        this.status = status;
    }
}
