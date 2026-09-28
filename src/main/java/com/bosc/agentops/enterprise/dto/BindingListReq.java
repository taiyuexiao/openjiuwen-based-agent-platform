package com.bosc.agentops.enterprise.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 绑定列表：按项目过滤；agentId 可选（精确到单个 Agent）。
 */
public class BindingListReq {

    @NotNull
    private Long projectId;

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
