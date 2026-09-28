package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 按 agent 过滤的列表请求（制品/分级结论）。
 */
public class ArtifactListReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

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
}
