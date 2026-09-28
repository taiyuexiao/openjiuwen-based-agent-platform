package com.bosc.agentops.agentdev.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 版本详情查询：按 agent + 版本号定位。 */
public class VersionDetailReq {

    @NotNull
    private Long projectId;

    @NotBlank
    private String version;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
