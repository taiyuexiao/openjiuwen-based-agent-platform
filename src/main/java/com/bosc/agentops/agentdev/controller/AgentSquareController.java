package com.bosc.agentops.agentdev.controller;

import com.bosc.agentops.agentdev.dto.AgentSquareItem;
import com.bosc.agentops.agentdev.dto.AgentSquareListReq;
import com.bosc.agentops.agentdev.service.AgentSquareService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 广场：平台级只读接口（认证即可，不做项目过滤），
 * 仅返回 visibility=PUBLIC 且 status=ACTIVE 的 Agent。
 */
@RestController
@RequestMapping("/v1/api/agent-square")
public class AgentSquareController {

    private final AgentSquareService agentSquareService;

    public AgentSquareController(AgentSquareService agentSquareService) {
        this.agentSquareService = agentSquareService;
    }

    @PostMapping("/list")
    @RequirePermission(value = "agent:read", projectScoped = false)
    public ApiResponse<List<AgentSquareItem>> list(@RequestBody(required = false) AgentSquareListReq req) {
        return ApiResponse.ok(agentSquareService.list(req));
    }
}
