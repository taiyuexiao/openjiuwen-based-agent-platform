package com.bosc.agentops.modelknowledge.dto;

/**
 * 知识库引用列表过滤条件（均可空）。
 */
public class KbRefListReq {

    /** 可空；传则只看该 Agent 的引用，不传返回项目全部引用 */
    private Long agentId;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }
}
