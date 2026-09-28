package com.bosc.agentops.governance.entity;

/**
 * 调用记录状态：SUCCESS=转发成功；FAILED=转发后上游错误/中断；
 * REJECTED=前置拒绝（无策略、限流、无路由等，未触达上游）。
 */
public enum InvocationStatus {
    SUCCESS,
    FAILED,
    REJECTED
}
