package com.bosc.agentops.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 绑定主归属应用：agentId + appCode → 调 CMDB 拉权威信息落快照。
 */
public class BindReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotBlank
    @Size(max = 64)
    private String appCode;

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

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }
}
