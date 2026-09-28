package com.bosc.agentops.enterprise.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 绑定操作（resync/unbind/detail）：按 agentId 定位（agent_id 唯一）。
 */
public class BindingOperateReq {

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
