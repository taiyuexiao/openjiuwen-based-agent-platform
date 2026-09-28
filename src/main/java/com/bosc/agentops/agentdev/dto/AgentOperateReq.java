package com.bosc.agentops.agentdev.dto;

import jakarta.validation.constraints.NotNull;

/** Agent 归档/详情等按 id 操作的请求。 */
public class AgentOperateReq {

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
