package com.bosc.agentops.governance.dto;

import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 路由同步请求：从 delivery 指定 agent+env 的 RUNNING 部署生成/更新 ServiceRoute。
 * agentVersion 由请求显式指定并与 RUNNING 部署所属发布单的版本一致性校验。
 */
public class RouteSyncReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotNull
    private EnvType env;

    @NotBlank
    private String agentVersion;

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

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }
}
