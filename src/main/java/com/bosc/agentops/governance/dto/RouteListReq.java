package com.bosc.agentops.governance.dto;

import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.NotNull;

/**
 * 路由列表查询：项目范围内按 agentId/env 过滤。
 */
public class RouteListReq {

    @NotNull
    private Long projectId;

    private Long agentId;

    private EnvType env;

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
}
