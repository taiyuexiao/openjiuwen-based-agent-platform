package com.bosc.agentops.agentdev.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新 Agent 元信息。code/accessMode 创建后不可变；runtimeEndpoint/healthEndpoint 仅 HOSTED 可改。
 */
public class AgentUpdateReq {

    @NotNull
    private Long id;

    @NotNull
    private Long projectId;

    @Size(max = 128)
    private String name;

    @Size(max = 1024)
    private String description;

    @Size(max = 512)
    private String runtimeEndpoint;

    @Size(max = 512)
    private String healthEndpoint;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
