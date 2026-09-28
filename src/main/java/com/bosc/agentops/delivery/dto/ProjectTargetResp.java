package com.bosc.agentops.delivery.dto;

import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.ProjectDeployTarget;

/**
 * 项目可选部署目标视图：关联记录 + 目标详情。
 */
public class ProjectTargetResp {

    private final Long id;
    private final Long projectId;
    private final Long targetId;
    private final Boolean isDefault;
    private final DeployTarget target;

    public ProjectTargetResp(ProjectDeployTarget mapping, DeployTarget target) {
        this.id = mapping.getId();
        this.projectId = mapping.getProjectId();
        this.targetId = mapping.getTargetId();
        this.isDefault = mapping.getIsDefault();
        this.target = target;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public DeployTarget getTarget() {
        return target;
    }
}
