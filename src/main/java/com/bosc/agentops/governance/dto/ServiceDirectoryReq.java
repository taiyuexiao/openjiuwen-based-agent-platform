package com.bosc.agentops.governance.dto;

import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.NotNull;

/**
 * 服务目录查询：项目范围内按 env 过滤已发布（存在 ACTIVE 路由）的 Agent 服务。
 */
public class ServiceDirectoryReq {

    @NotNull
    private Long projectId;

    private EnvType env;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }
}
