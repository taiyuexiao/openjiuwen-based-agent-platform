package com.bosc.agentops.enterprise.connector;

/**
 * CMDB 应用状态。仅 ACTIVE 允许绑定；DISABLED（已下线）触发绑定拒绝 / 重同步置 STALE。
 */
public enum CmdbAppStatus {
    ACTIVE,
    DISABLED
}
