package com.bosc.agentops.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 发布单：发布状态机唯一事实源（执行器只执行动作，不持有状态）。
 * gateResult 为门禁四项检查聚合 JSON；levelSnapshot 在 gate 通过时固化；
 * rollbackOf 非空表示本单由回滚生成，指向被回滚的发布单。
 */
@TableName("delivery_release")
public class Release {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String agentVersion;
    private Long artifactId;
    private Long targetId;
    private String levelSnapshot;
    private ReleaseStatus status;
    private String gateResult;
    /** 行内审批单号（占位，approve 时必填） */
    private String approvalRef;
    private Long rollbackOf;
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

    public Long getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(Long artifactId) {
        this.artifactId = artifactId;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getLevelSnapshot() {
        return levelSnapshot;
    }

    public void setLevelSnapshot(String levelSnapshot) {
        this.levelSnapshot = levelSnapshot;
    }

    public ReleaseStatus getStatus() {
        return status;
    }

    public void setStatus(ReleaseStatus status) {
        this.status = status;
    }

    public String getGateResult() {
        return gateResult;
    }

    public void setGateResult(String gateResult) {
        this.gateResult = gateResult;
    }

    public String getApprovalRef() {
        return approvalRef;
    }

    public void setApprovalRef(String approvalRef) {
        this.approvalRef = approvalRef;
    }

    public Long getRollbackOf() {
        return rollbackOf;
    }

    public void setRollbackOf(Long rollbackOf) {
        this.rollbackOf = rollbackOf;
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
