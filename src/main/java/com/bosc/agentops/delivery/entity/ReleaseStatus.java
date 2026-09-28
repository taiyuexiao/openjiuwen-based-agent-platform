package com.bosc.agentops.delivery.entity;

/**
 * 发布单状态机：DRAFT →（gate）→ GATED →（approve）→ APPROVED →（deploy）→ DEPLOYING
 * →（健康检查通过）→ RUNNING；deploy 失败 → FAILED；被回滚 → ROLLED_BACK。
 */
public enum ReleaseStatus {
    DRAFT,
    GATED,
    APPROVED,
    DEPLOYING,
    RUNNING,
    FAILED,
    ROLLED_BACK
}
