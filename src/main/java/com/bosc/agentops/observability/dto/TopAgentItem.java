package com.bosc.agentops.observability.dto;

/** 按调用量的 Agent 排行项。 */
public class TopAgentItem {

    private Long agentId;
    private long calls;

    public TopAgentItem() {
    }

    public TopAgentItem(Long agentId, long calls) {
        this.agentId = agentId;
        this.calls = calls;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public long getCalls() {
        return calls;
    }

    public void setCalls(long calls) {
        this.calls = calls;
    }
}
