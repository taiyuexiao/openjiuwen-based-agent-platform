package com.bosc.agentops.agentdev.entity;

/**
 * Agent 版本状态：REGISTERED=已登记（声明已通过校验且不可变）。DRAFT 预留给后续草稿态。
 */
public enum AgentVersionStatus {
    DRAFT,
    REGISTERED
}
