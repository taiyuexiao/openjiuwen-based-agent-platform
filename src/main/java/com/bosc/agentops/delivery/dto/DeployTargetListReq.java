package com.bosc.agentops.delivery.dto;

import com.bosc.agentops.delivery.entity.DeployTargetStatus;
import com.bosc.agentops.project.entity.EnvType;

/**
 * 部署目标列表过滤（平台级）。
 */
public class DeployTargetListReq {

    private EnvType env;

    private DeployTargetStatus status;

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public DeployTargetStatus getStatus() {
        return status;
    }

    public void setStatus(DeployTargetStatus status) {
        this.status = status;
    }
}
