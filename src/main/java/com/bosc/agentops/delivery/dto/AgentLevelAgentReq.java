package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 分级结论 disable/list 请求（按 agent）。
 */
public class AgentLevelAgentReq {

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
