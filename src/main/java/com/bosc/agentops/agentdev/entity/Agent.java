package com.bosc.agentops.agentdev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Agent 主表。accessMode 决定纳管边界：NATIVE 完整声明校验；ADAPTED 声明可空（空则能力降级）；
 * HOSTED 不登记声明，必须记录 runtimeEndpoint/healthEndpoint，平台仅承诺服务调用/生命周期/基础日志。
 */
@TableName("agent")
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 项目内唯一 */
    private String code;
    private String name;
    private Long projectId;
    private AccessMode accessMode;
    /** HOSTED 模式的运行入口 */
    private String runtimeEndpoint;
    /** HOSTED 模式的健康检查地址 */
    private String healthEndpoint;
    private String description;
    private AgentStatus status;
    /** PROJECT=仅所属项目可见；PUBLIC=发布到 Agent 广场 */
    private AgentVisibility visibility;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(AccessMode accessMode) {
        this.accessMode = accessMode;
    }

    public String getRuntimeEndpoint() {
        return runtimeEndpoint;
    }

    public void setRuntimeEndpoint(String runtimeEndpoint) {
        this.runtimeEndpoint = runtimeEndpoint;
    }

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public void setHealthEndpoint(String healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public AgentVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(AgentVisibility visibility) {
        this.visibility = visibility;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
