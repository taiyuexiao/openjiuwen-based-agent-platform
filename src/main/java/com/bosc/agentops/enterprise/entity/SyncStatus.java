package com.bosc.agentops.enterprise.entity;

/**
 * 绑定同步状态：SYNCED=已同步；STALE=超过阈值未重同步（读取/门禁时动态判定）或重同步时发现应用已停用；
 * FAILED=重同步时 CMDB 查无应用；UNVERIFIED=已绑定但未完成首次同步（预留）。
 */
public enum SyncStatus {
    SYNCED,
    STALE,
    FAILED,
    UNVERIFIED
}
