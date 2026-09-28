package com.bosc.agentops.observability.dto;

/** 观测统计总览查询：projectId 可空，空=平台级聚合。 */
public class StatsOverviewReq {

    private Long projectId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
