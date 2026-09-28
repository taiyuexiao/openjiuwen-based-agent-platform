package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.dto.McpPolicyGrantReq;
import com.bosc.agentops.governance.dto.McpPolicyListReq;
import com.bosc.agentops.governance.entity.McpInvokePolicy;
import com.bosc.agentops.governance.service.McpPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 调用策略管理：grant（upsert allowed）、revoke（删除，回到 fail-closed）、list。
 */
@RestController
@RequestMapping("/v1/api/mcp-policies")
public class McpPolicyController {

    private final McpPolicyService mcpPolicyService;

    public McpPolicyController(McpPolicyService mcpPolicyService) {
        this.mcpPolicyService = mcpPolicyService;
    }

    @PostMapping("/grant")
    @RequirePermission("gov:policy")
    public ApiResponse<McpInvokePolicy> grant(@Valid @RequestBody McpPolicyGrantReq req) {
        return ApiResponse.ok(mcpPolicyService.grant(req));
    }

    @PostMapping("/revoke")
    @RequirePermission("gov:policy")
    public ApiResponse<Void> revoke(@Valid @RequestBody GovIdReq req) {
        mcpPolicyService.revoke(req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("gov:read")
    public ApiResponse<List<McpInvokePolicy>> list(@Valid @RequestBody McpPolicyListReq req) {
        return ApiResponse.ok(mcpPolicyService.list(req));
    }
}
