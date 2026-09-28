package com.bosc.agentops.assethub.dto;

import com.bosc.agentops.assethub.entity.AssetType;
import jakarta.validation.constraints.NotNull;

/** 资产列表：返回归属该项目，或 SHARED 且已授权该项目的资产。 */
public class AssetListReq {

    @NotNull
    private Long projectId;

    /** 可选类型过滤 */
    private AssetType assetType;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }
}
