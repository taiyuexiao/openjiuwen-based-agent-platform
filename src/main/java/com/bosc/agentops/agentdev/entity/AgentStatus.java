package com.bosc.agentops.agentdev.entity;

/**
 * Agent 状态：ACTIVE → ARCHIVED。归档后禁止登记新版本。
 */
public enum AgentStatus {
    ACTIVE,
    ARCHIVED
}
