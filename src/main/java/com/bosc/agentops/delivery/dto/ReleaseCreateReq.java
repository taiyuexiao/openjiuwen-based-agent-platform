package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建发布单（DRAFT）。artifactId 必须与 agentId/agentVersion 一致（制品-版本可追溯）。
 */
public class ReleaseCreateReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotBlank
    @Size(max = 32)
    private String agentVersion;

    @NotNull
    private Long artifactId;

    @NotNull
    private Long targetId;

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
}
