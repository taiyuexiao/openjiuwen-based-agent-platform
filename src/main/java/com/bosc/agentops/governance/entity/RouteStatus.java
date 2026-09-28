package com.bosc.agentops.governance.entity;

/**
 * 服务路由状态：ACTIVE=当前承载流量；DRAINED=已被新路由顶替，不再路由。
 */
public enum RouteStatus {
    ACTIVE,
    DRAINED
}
