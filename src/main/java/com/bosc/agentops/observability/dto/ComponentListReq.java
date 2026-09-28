package com.bosc.agentops.observability.dto;

import com.bosc.agentops.observability.entity.ComponentType;
import jakarta.validation.constraints.NotNull;

/**
 * 组件列表查询。
 */
public class ComponentListReq {

    @NotNull
    private Long projectId;

    private ComponentType type;

    private Boolean critical;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType type) {
        this.type = type;
    }

    public Boolean getCritical() {
        return critical;
    }

    public void setCritical(Boolean critical) {
        this.critical = critical;
    }
}
