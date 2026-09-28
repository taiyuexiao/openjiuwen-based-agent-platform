package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotNull;

/** 资产级操作（detail/publish/offline）入参：id + 操作所在项目。 */
public class AssetOperateReq {

    @NotNull
    private Long id;

    @NotNull
    private Long projectId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
