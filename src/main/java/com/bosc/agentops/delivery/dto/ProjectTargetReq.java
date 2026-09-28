package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 项目部署目标操作（attach/detach/set-default 共用）。
 */
public class ProjectTargetReq {

    @NotNull
    private Long targetId;

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }
}
