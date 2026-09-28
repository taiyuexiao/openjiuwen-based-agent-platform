package com.bosc.agentops.delivery.entity;

/**
 * 部署实例健康状态。HOSTED（外部托管）实例平台无法探活，恒为 UNKNOWN。
 */
public enum HealthStatus {
    HEALTHY,
    UNHEALTHY,
    UNKNOWN
}
