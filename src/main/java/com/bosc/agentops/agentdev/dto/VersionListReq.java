package com.bosc.agentops.agentdev.dto;

import jakarta.validation.constraints.NotNull;

/** 版本列表查询：按 agent 定位，projectId 用于权限 scope 与归属校验。 */
public class VersionListReq {

    @NotNull
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
