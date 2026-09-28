package com.bosc.agentops.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 制品：登记后不可变（无 update 接口），(agentId, agentVersion) 唯一。
 * evaluationRef 为评测报告关联标识（占位，评测平台对接后校验其状态）。
 */
@TableName("delivery_artifact")
public class Artifact {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentVersion;
    private String codeCommit;
    private String imageDigest;
    private String configDigest;
    private String evaluationRef;
    private LocalDateTime builtAt;
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

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getCodeCommit() {
        return codeCommit;
    }

    public void setCodeCommit(String codeCommit) {
        this.codeCommit = codeCommit;
    }

    public String getImageDigest() {
        return imageDigest;
    }

    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }

    public String getConfigDigest() {
        return configDigest;
    }

    public void setConfigDigest(String configDigest) {
        this.configDigest = configDigest;
    }

    public String getEvaluationRef() {
        return evaluationRef;
    }

    public void setEvaluationRef(String evaluationRef) {
        this.evaluationRef = evaluationRef;
    }

    public LocalDateTime getBuiltAt() {
        return builtAt;
    }

    public void setBuiltAt(LocalDateTime builtAt) {
        this.builtAt = builtAt;
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
