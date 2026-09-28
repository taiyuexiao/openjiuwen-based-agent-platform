package com.bosc.agentops.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bosc.agentops.project.entity.EnvType;

import java.time.LocalDateTime;

/**
 * 部署目标：平台级资源。baseResource / allowedAgentLevels 为 JSON 文本
 * （如 {"cpu":"2","memory":"4Gi"} / ["P1","P2"]）。
 */
@TableName("delivery_deploy_target")
public class DeployTarget {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private EnvType env;
    private String cluster;
    private String namespace;
    private String baseResource;
    private String allowedAgentLevels;
    private DeployTargetStatus status;
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

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getBaseResource() {
        return baseResource;
    }

    public void setBaseResource(String baseResource) {
        this.baseResource = baseResource;
    }

    public String getAllowedAgentLevels() {
        return allowedAgentLevels;
    }

    public void setAllowedAgentLevels(String allowedAgentLevels) {
        this.allowedAgentLevels = allowedAgentLevels;
    }

    public DeployTargetStatus getStatus() {
        return status;
    }

    public void setStatus(DeployTargetStatus status) {
        this.status = status;
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
