package com.bosc.agentops.agentdev.entity;

/**
 * Agent 接入方式：NATIVE=原生接入（完整声明校验+轨迹观测）；ADAPTED=适配接入（声明可空，
 * 空声明登记时标记能力降级）；HOSTED=运行托管（不登记声明，只记录运行入口与健康检查地址）。
 */
public enum AccessMode {
    NATIVE,
    ADAPTED,
    HOSTED
}
