package com.bosc.agentops.agentdev.dto;

import com.bosc.agentops.agentdev.entity.AccessMode;
import com.bosc.agentops.agentdev.entity.AgentStatus;
import jakarta.validation.constraints.NotNull;

/** Agent 列表查询：按项目过滤，可选接入方式/状态。 */
public class AgentListReq {

    @NotNull
    private Long projectId;

    private AccessMode accessMode;

    private AgentStatus status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(AccessMode accessMode) {
        this.accessMode = accessMode;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }
}
