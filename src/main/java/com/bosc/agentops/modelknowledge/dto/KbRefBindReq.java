package com.bosc.agentops.modelknowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 知识库引用绑定。agentId 可空（空=项目级引用）；Agent 实体由模块 02 提供，本期为弱引用。
 */
public class KbRefBindReq {

    @NotNull
    private Long kbId;

    /** 可空，空=项目级引用 */
    private Long agentId;

    /** 引用版本（如知识库快照版本） */
    @Size(max = 64)
    private String refVersion;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getRefVersion() {
        return refVersion;
    }

    public void setRefVersion(String refVersion) {
        this.refVersion = refVersion;
    }
}
