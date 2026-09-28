package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 处置动作：吊销 CallerPolicy（暂停调用）。reason 落审计。
 */
public class RevokeCallerReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long policyId;

    private String reason;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
