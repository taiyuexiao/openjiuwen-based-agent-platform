package com.bosc.agentops.modelknowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 项目级模型授权：paramPolicy 为允许覆盖的参数白名单/上下限（JSON），授权时冻结范围。
 */
@TableName("project_model_grant")
public class ProjectModelGrant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long modelServiceId;
    /** 参数策略 JSON：{ "&lt;参数名&gt;": {"value": 覆盖值, "min": 下限, "max": 上限} } */
    private String paramPolicy;
    private String grantedBy;
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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getModelServiceId() {
        return modelServiceId;
    }

    public void setModelServiceId(Long modelServiceId) {
        this.modelServiceId = modelServiceId;
    }

    public String getParamPolicy() {
        return paramPolicy;
    }

    public void setParamPolicy(String paramPolicy) {
        this.paramPolicy = paramPolicy;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
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
