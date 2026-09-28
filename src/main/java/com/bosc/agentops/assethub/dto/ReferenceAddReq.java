package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 登记引用：projectId 为消费方项目；引用必须锁定到版本。 */
public class ReferenceAddReq {

    /** 消费方项目 */
    @NotNull
    private Long projectId;

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "版本号必须为 x.y.z 格式")
    private String assetVersion;

    /** 可空，空=项目级引用；弱引用，Agent 实体尚未落地 */
    private Long agentId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getAssetVersion() {
        return assetVersion;
    }

    public void setAssetVersion(String assetVersion) {
        this.assetVersion = assetVersion;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }
}
