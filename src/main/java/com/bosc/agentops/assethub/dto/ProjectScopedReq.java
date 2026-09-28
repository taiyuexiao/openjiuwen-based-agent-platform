package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotNull;

/** 仅携带 projectId 的查询入参（versions/list、grants/list、references/list、tools/list）。 */
public class ProjectScopedReq {

    @NotNull
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
