package com.bosc.agentops.observability.entity;

/**
 * 告警状态：FIRING（触发中）→ HANDLED（已登记处置）→ CLOSED（关闭，预留）。
 */
public enum AlertStatus {
    FIRING,
    HANDLED,
    CLOSED
}
