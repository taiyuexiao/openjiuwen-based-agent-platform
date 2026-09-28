package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.governance.dto.AgentAuthCheckReq;
import com.bosc.agentops.governance.dto.CheckResp;
import com.bosc.agentops.governance.dto.McpCheckReq;
import com.bosc.agentops.governance.service.AgentAuthService;
import com.bosc.agentops.governance.service.McpPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时鉴权 PEP（机器间接口，不走管理面 @RequirePermission 体系）：
 * /v1/mcp-check 由 Agent 运行时或 MCP 网关调用；/v1/agent-auth/check 用于 Agent 间调用鉴权。
 * 判定结果以 allowed + reason 返回，fail-closed。
 */
@RestController
public class RuntimeCheckController {

    private final McpPolicyService mcpPolicyService;
    private final AgentAuthService agentAuthService;

    public RuntimeCheckController(McpPolicyService mcpPolicyService, AgentAuthService agentAuthService) {
        this.mcpPolicyService = mcpPolicyService;
        this.agentAuthService = agentAuthService;
    }

    @PostMapping("/v1/mcp-check")
    public ApiResponse<CheckResp> mcpCheck(@Valid @RequestBody McpCheckReq req) {
        return ApiResponse.ok(mcpPolicyService.check(req.getAgentId(), req.getToolAssetId(), req.getEnv()));
    }

    @PostMapping("/v1/agent-auth/check")
    public ApiResponse<CheckResp> agentAuthCheck(@Valid @RequestBody AgentAuthCheckReq req) {
        return ApiResponse.ok(agentAuthService.check(req.getCallerAgentCode(),
                req.getCalleeAgentCode(), req.getEnv()));
    }
}
