package com.bosc.agentops.governance.dto;

import com.bosc.agentops.governance.entity.CallerType;
import com.bosc.agentops.project.entity.EnvType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 调用方授权：建立 caller → agent + env 的调用许可与流控参数。
 * sharedToken 可选：非空时运行时调用必须携带匹配 Bearer 共享密钥。
 */
public class CallerPolicyGrantReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long agentId;

    @NotNull
    private EnvType env;

    @NotNull
    private CallerType callerType;

    @NotBlank
    private String callerId;

    private String sharedToken;

    @Min(1)
    private Integer rateLimitPerMin;

    @Min(100)
    private Integer timeoutMs;

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

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public CallerType getCallerType() {
        return callerType;
    }

    public void setCallerType(CallerType callerType) {
        this.callerType = callerType;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getSharedToken() {
        return sharedToken;
    }

    public void setSharedToken(String sharedToken) {
        this.sharedToken = sharedToken;
    }

    public Integer getRateLimitPerMin() {
        return rateLimitPerMin;
    }

    public void setRateLimitPerMin(Integer rateLimitPerMin) {
        this.rateLimitPerMin = rateLimitPerMin;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
