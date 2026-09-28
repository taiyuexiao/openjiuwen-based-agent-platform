package com.bosc.agentops.assethub.entity;

/**
 * 资产状态：DRAFT → PUBLISHED → OFFLINE。OFFLINE 禁止新引用，已有引用记录保留。
 */
public enum AssetStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE
}
