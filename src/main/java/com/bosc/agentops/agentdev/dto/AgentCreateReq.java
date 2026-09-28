package com.bosc.agentops.agentdev.dto;

import com.bosc.agentops.agentdev.entity.AccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建 Agent。accessMode 缺省 NATIVE；HOSTED 模式必须提供 runtimeEndpoint + healthEndpoint。
 */
public class AgentCreateReq {

    @NotBlank
    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private Long projectId;

    private AccessMode accessMode;

    @Size(max = 1024)
    private String description;

    @Size(max = 512)
    private String runtimeEndpoint;

    @Size(max = 512)
    private String healthEndpoint;

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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(AccessMode accessMode) {
        this.accessMode = accessMode;
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
