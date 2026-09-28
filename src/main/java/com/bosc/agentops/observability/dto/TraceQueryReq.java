package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 链路查询：按 traceId / agentId / env / 落库时间窗过滤，结果按项目权限隔离。
 */
public class TraceQueryReq {

    @NotNull
    private Long projectId;

    private String traceId;

    private Long agentId;

    private String env;

    /** 落库时间窗起点（created_at >=） */
    private LocalDateTime createdFrom;

    /** 落库时间窗终点（created_at <=） */
    private LocalDateTime createdTo;

    /** 默认 200，上限 1000 */
    private Integer limit;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDateTime createdTo) {
        this.createdTo = createdTo;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
