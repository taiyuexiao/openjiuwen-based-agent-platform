package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 处置动作：路由停流量（调 governance 把 route 置 DRAINED）。reason 落审计。
 */
public class DisableRouteReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long routeId;

    private String reason;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
