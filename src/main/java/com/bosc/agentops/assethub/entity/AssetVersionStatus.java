package com.bosc.agentops.assethub.entity;

/**
 * 资产版本状态。版本不可变：发布后 definition 不再修改，变更只能发新版本。
 * DRAFT 仅由文件上传产生（上传只落草稿，发布走既有 publish 流程）。
 */
public enum AssetVersionStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE
}
