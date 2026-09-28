package com.bosc.agentops.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MCP 调用鉴权 PEP 请求（运行时面，由 Agent 运行时或 MCP 网关调用）。
 */
public class McpCheckReq {

    @NotNull
    private Long agentId;

    @NotNull
    private Long toolAssetId;

    @NotBlank
    private String env;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getToolAssetId() {
        return toolAssetId;
    }

    public void setToolAssetId(Long toolAssetId) {
        this.toolAssetId = toolAssetId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }
}
