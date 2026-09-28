package com.bosc.agentops.enterprise.dto;

import jakarta.validation.constraints.NotNull;

/**
 * ITSM 变更记录查询：按项目过滤；agentId 可选。
 */
public class ItsmChangeListReq {

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
