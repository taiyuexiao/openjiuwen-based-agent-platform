package com.bosc.agentops.observability.dto;

import com.bosc.agentops.observability.entity.AlertStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 告警列表查询：项目内 Agent 的告警 + 平台级（agentId 为空）告警。
 */
public class AlertListReq {

    @NotNull
    private Long projectId;

    private AlertStatus status;

    private String level;

    private Long agentId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }
}
