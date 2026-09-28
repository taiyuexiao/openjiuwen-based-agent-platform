package com.bosc.agentops.governance.entity;

/**
 * 调用方策略状态：ACTIVE=生效；REVOKED=已撤销（等同于不存在，fail-closed）。
 */
public enum PolicyStatus {
    ACTIVE,
    REVOKED
}
