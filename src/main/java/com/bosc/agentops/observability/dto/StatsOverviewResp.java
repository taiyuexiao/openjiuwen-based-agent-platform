package com.bosc.agentops.observability.dto;

import java.util.List;
import java.util.Map;

/**
 * 观测统计总览：近 7 天（含今天，按自然日）调用聚合。
 * scope=PROJECT 时按项目下 Agent 过滤；scope=PLATFORM 时全平台聚合。
 */
public class StatsOverviewResp {

    /** PROJECT / PLATFORM */
    private String scope;
    /** 平台级聚合时为 null */
    private Long projectId;
    private long totalCalls;
    /** 0~1；无调用时为 0 */
    private double successRate;
    /** 毫秒；无调用时为 0 */
    private double avgLatencyMs;
    /** 近 7 天逐日趋势（含今天，升序，无数据日为零值） */
    private List<DailyTrendItem> dailyTrend;
    /** 调用量 top 5 */
    private List<TopAgentItem> topAgents;
    /** spanKind → span 数 */
    private Map<String, Long> spanKindDist;
    /** FIRING 告警数 */
    private long alertOpenCount;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public List<DailyTrendItem> getDailyTrend() {
        return dailyTrend;
    }

    public void setDailyTrend(List<DailyTrendItem> dailyTrend) {
        this.dailyTrend = dailyTrend;
    }

    public List<TopAgentItem> getTopAgents() {
        return topAgents;
    }

    public void setTopAgents(List<TopAgentItem> topAgents) {
        this.topAgents = topAgents;
    }

    public Map<String, Long> getSpanKindDist() {
        return spanKindDist;
    }

    public void setSpanKindDist(Map<String, Long> spanKindDist) {
        this.spanKindDist = spanKindDist;
    }

    public long getAlertOpenCount() {
        return alertOpenCount;
    }

    public void setAlertOpenCount(long alertOpenCount) {
        this.alertOpenCount = alertOpenCount;
    }
}
