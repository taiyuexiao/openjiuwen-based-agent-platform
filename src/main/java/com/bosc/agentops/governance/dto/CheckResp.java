package com.bosc.agentops.governance.dto;

/**
 * 运行时鉴权判定结果：allowed + reason（拒绝时给出原因）。
 */
public class CheckResp {

    private boolean allowed;
    private String reason;

    public CheckResp() {
    }

    public CheckResp(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
