package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 按 id 定位观测资源的通用请求（带 projectId 供权限 scope 解析）。
 */
public class ObsIdReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long id;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
