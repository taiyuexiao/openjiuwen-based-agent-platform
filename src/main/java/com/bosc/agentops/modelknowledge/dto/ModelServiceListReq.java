package com.bosc.agentops.modelknowledge.dto;

/**
 * 模型服务列表过滤条件（均可空）。
 */
public class ModelServiceListReq {

    private Long providerId;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }
}
