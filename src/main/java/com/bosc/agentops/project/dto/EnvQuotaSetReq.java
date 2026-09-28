package com.bosc.agentops.project.dto;

import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.NotNull;

public class EnvQuotaSetReq {

    @NotNull
    private EnvType env;

    /** 资源配额 JSON 字符串 */
    private String resourceQuota;

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public String getResourceQuota() {
        return resourceQuota;
    }

    public void setResourceQuota(String resourceQuota) {
        this.resourceQuota = resourceQuota;
    }
}
