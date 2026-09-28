package com.bosc.agentops.agentdev.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Agent 版本：登记即 REGISTERED 且 declaration 不可变，变更只能登记新版本。
 * declaration 为声明清单 JSON 文本：{model:{modelCode,params}, prompt:{...},
 * skills:[{assetId,version}], mcpTools:[{assetId,version}], knowledgeBases:[{kbId}], memory:{...}}。
 */
@TableName("agent_version")
public class AgentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    /** x.y.z，同 agent 下唯一 */
    private String version;
    /** 声明清单 JSON；ADAPTED 空声明登记时为空 */
    private String declaration;
    /** ADAPTED 空声明登记时置真：平台管理能力降级（无法识别依赖、无法做完整校验） */
    private Boolean capabilityDegraded;
    private AgentVersionStatus status;
    private String registeredBy;
    private LocalDateTime registeredAt;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDeclaration() {
        return declaration;
    }

    public void setDeclaration(String declaration) {
        this.declaration = declaration;
    }

    public Boolean getCapabilityDegraded() {
        return capabilityDegraded;
    }

    public void setCapabilityDegraded(Boolean capabilityDegraded) {
        this.capabilityDegraded = capabilityDegraded;
    }

    public AgentVersionStatus getStatus() {
        return status;
    }

    public void setStatus(AgentVersionStatus status) {
        this.status = status;
    }

    public String getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(String registeredBy) {
        this.registeredBy = registeredBy;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
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
