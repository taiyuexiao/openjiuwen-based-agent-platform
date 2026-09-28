package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 制品登记。制品不可变：登记后不提供修改接口，(agentId, agentVersion) 唯一。
 * codeCommit/imageDigest/configDigest/evaluationRef 登记时可缺，发布门禁「制品完整」项要求四者非空。
 */
public class ArtifactRegisterReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotBlank
    @Size(max = 32)
    private String agentVersion;

    @Size(max = 64)
    private String codeCommit;

    @Size(max = 256)
    private String imageDigest;

    @Size(max = 128)
    private String configDigest;

    @Size(max = 128)
    private String evaluationRef;

    private LocalDateTime builtAt;

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

    public String getCodeCommit() {
        return codeCommit;
    }

    public void setCodeCommit(String codeCommit) {
        this.codeCommit = codeCommit;
    }

    public String getImageDigest() {
        return imageDigest;
    }

    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }

    public String getConfigDigest() {
        return configDigest;
    }

    public void setConfigDigest(String configDigest) {
        this.configDigest = configDigest;
    }

    public String getEvaluationRef() {
        return evaluationRef;
    }

    public void setEvaluationRef(String evaluationRef) {
        this.evaluationRef = evaluationRef;
    }

    public LocalDateTime getBuiltAt() {
        return builtAt;
    }

    public void setBuiltAt(LocalDateTime builtAt) {
        this.builtAt = builtAt;
    }
}
