package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 链路还原请求：POST /v1/api/observability/traces/{traceId}，body 带 projectId 供权限 scope 解析。
 */
public class TraceDetailReq {

    @NotNull
    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
