package com.bosc.agentops.governance.dto;

import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.NotNull;

/**
 * MCP 调用策略授予：同一 (agentId, assetId, env) 重复授予时更新 allowed 取值（upsert）。
 */
public class McpPolicyGrantReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotNull
    private Long assetId;

    @NotNull
    private EnvType env;

    @NotNull
    private Boolean allowed;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public Boolean getAllowed() {
        return allowed;
    }

    public void setAllowed(Boolean allowed) {
        this.allowed = allowed;
    }
}
