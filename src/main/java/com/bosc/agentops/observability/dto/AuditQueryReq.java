package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 审计查询：复用 foundation 的 audit_event 表；返回时对 detail JSON 兜底脱敏。
 */
public class AuditQueryReq {

    @NotNull
    private Long projectId;

    private String module;

    /** 操作人 */
    private String userId;

    private String resourceType;

    private String resourceId;

    private LocalDateTime createdFrom;

    private LocalDateTime createdTo;

    /** 默认 100，上限 500 */
    private Integer limit;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
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
