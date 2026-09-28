package com.bosc.agentops.delivery.dto;

import com.bosc.agentops.delivery.entity.AgentLevelValue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 录入 Agent 分级结论（外部评审形成）。confirm 新等级后旧生效记录自动失效。
 */
public class AgentLevelConfirmReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotNull
    private AgentLevelValue level;

    /** 评审来源说明，如「2026Q3 投产评审会-纪要我链接/单号」 */
    @Size(max = 256)
    private String source;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public AgentLevelValue getLevel() {
        return level;
    }

    public void setLevel(AgentLevelValue level) {
        this.level = level;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
