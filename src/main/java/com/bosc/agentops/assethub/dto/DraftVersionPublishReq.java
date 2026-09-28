package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 发布草稿版本：把上传产生的 DRAFT 版本置为 PUBLISHED。 */
public class DraftVersionPublishReq {

    @NotNull
    private Long assetId;

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "版本号必须为 x.y.z 格式")
    private String version;

    @NotNull
    private Long projectId;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
