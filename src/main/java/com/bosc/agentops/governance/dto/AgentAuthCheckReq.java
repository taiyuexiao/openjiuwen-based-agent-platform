package com.bosc.agentops.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Agent 间调用鉴权请求（运行时面）：callerAgentCode 是否允许调用 calleeAgentCode。
 */
public class AgentAuthCheckReq {

    @NotBlank
    private String callerAgentCode;

    @NotBlank
    private String calleeAgentCode;

    @NotBlank
    private String env;

    public String getCallerAgentCode() {
        return callerAgentCode;
    }

    public void setCallerAgentCode(String callerAgentCode) {
        this.callerAgentCode = callerAgentCode;
    }

    public String getCalleeAgentCode() {
        return calleeAgentCode;
    }

    public void setCalleeAgentCode(String calleeAgentCode) {
        this.calleeAgentCode = calleeAgentCode;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}
