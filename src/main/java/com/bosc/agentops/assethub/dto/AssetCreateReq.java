package com.bosc.agentops.assethub.dto;

import com.bosc.agentops.assethub.entity.AssetType;
import com.bosc.agentops.assethub.entity.AssetVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AssetCreateReq {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "仅允许字母、数字、中划线")
    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private AssetType assetType;

    /** MCP_TOOL 归属的 MCP_SERVICE（可空，发布前必须归属） */
    private Long parentAssetId;

    /** 归属项目 */
    @NotNull
    private Long projectId;

    @NotNull
    private AssetVisibility visibility;

    @Size(max = 1024)
    private String description;

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

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public Long getParentAssetId() {
        return parentAssetId;
    }

    public void setParentAssetId(Long parentAssetId) {
        this.parentAssetId = parentAssetId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AssetVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(AssetVisibility visibility) {
        this.visibility = visibility;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
