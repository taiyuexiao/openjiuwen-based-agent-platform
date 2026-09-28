package com.bosc.agentops.observability.entity;

/**
 * 组件健康探测结果：2xx → UP；非 2xx / 连接异常 → DOWN；endpoint 未配置或不可解析 → UNKNOWN。
 */
public enum HealthStatus {
    UP,
    DOWN,
    UNKNOWN
}
