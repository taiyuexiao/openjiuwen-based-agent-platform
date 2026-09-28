package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.AgentLevelAgentReq;
import com.bosc.agentops.delivery.dto.AgentLevelConfirmReq;
import com.bosc.agentops.delivery.entity.AgentLevel;
import com.bosc.agentops.delivery.service.AgentLevelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 分级结论录入与查询。confirm/disable 归 delivery:target（部署治理配置面），
 * 与 approve（发布放行）保持分离。
 */
@RestController
@RequestMapping("/v1/api/agent-levels")
public class AgentLevelController {

    private final AgentLevelService agentLevelService;

    public AgentLevelController(AgentLevelService agentLevelService) {
        this.agentLevelService = agentLevelService;
    }

    @PostMapping("/confirm")
    @RequirePermission("delivery:target")
    public ApiResponse<AgentLevel> confirm(@Valid @RequestBody AgentLevelConfirmReq req) {
        return ApiResponse.ok(agentLevelService.confirm(req));
    }

    @PostMapping("/disable")
    @RequirePermission("delivery:target")
    public ApiResponse<Void> disable(@Valid @RequestBody AgentLevelAgentReq req) {
        agentLevelService.disable(req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("delivery:read")
    public ApiResponse<List<AgentLevel>> list(@Valid @RequestBody AgentLevelAgentReq req) {
        return ApiResponse.ok(agentLevelService.list(req));
    }
}
