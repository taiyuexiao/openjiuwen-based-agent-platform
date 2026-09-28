package com.bosc.agentops.modelknowledge.entity;

/**
 * 目录项状态。disable 后目录项不再下发（effectiveModels/listRefs 不返回），历史授权记录保留。
 */
public enum CatalogStatus {
    ENABLED,
    DISABLED
}
