package com.bosc.agentops.agentdev.dto;

import com.bosc.agentops.agentdev.entity.Agent;

/**
 * Agent 详情：实体 + 接入方式说明（capabilityNote）。
 * HOSTED 标注平台能力边界；ADAPTED 标注空声明降级语义。
 */
public class AgentDetailResp {

    private Agent agent;
    private String capabilityNote;

    public AgentDetailResp(Agent agent, String capabilityNote) {
        this.agent = agent;
        this.capabilityNote = capabilityNote;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public String getCapabilityNote() {
        return capabilityNote;
    }

    public void setCapabilityNote(String capabilityNote) {
        this.capabilityNote = capabilityNote;
    }
}
