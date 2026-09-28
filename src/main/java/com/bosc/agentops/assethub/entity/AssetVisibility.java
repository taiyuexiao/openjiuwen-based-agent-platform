package com.bosc.agentops.assethub.entity;

/**
 * 资产可见性：PROJECT=仅归属项目；SHARED=需 AssetGrant 显式授权的项目可见可用。默认不全局可见。
 */
public enum AssetVisibility {
    PROJECT,
    SHARED
}
