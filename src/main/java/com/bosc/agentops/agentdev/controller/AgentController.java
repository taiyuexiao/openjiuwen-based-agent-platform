package com.bosc.agentops.agentdev.controller;

import com.bosc.agentops.agentdev.dto.AgentCreateReq;
import com.bosc.agentops.agentdev.dto.AgentDetailResp;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.dto.AgentOperateReq;
import com.bosc.agentops.agentdev.dto.AgentUpdateReq;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent CRUD 与生命周期。权限 scope 从请求体 projectId 解析（getProjectId 约定）；
 * 所属项目一致性在 service 层二次校验。
 */
@RestController
@RequestMapping("/v1/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/create")
    @RequirePermission("agent:create")
    public ApiResponse<Agent> create(@Valid @RequestBody AgentCreateReq req) {
        return ApiResponse.ok(agentService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission("agent:update")
    public ApiResponse<Agent> update(@Valid @RequestBody AgentUpdateReq req) {
        return ApiResponse.ok(agentService.update(req));
    }

    @PostMapping("/archive")
    @RequirePermission("agent:archive")
    public ApiResponse<Agent> archive(@Valid @RequestBody AgentOperateReq req) {
        return ApiResponse.ok(agentService.archive(req));
    }

    @PostMapping("/publish")
    @RequirePermission("agent:update")
    public ApiResponse<Agent> publish(@Valid @RequestBody AgentOperateReq req) {
        return ApiResponse.ok(agentService.publish(req));
    }

    @PostMapping("/unpublish")
    @RequirePermission("agent:update")
    public ApiResponse<Agent> unpublish(@Valid @RequestBody AgentOperateReq req) {
        return ApiResponse.ok(agentService.unpublish(req));
    }

    @PostMapping("/detail")
    @RequirePermission("agent:read")
    public ApiResponse<AgentDetailResp> detail(@Valid @RequestBody AgentOperateReq req) {
        return ApiResponse.ok(agentService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("agent:read")
    public ApiResponse<List<Agent>> list(@Valid @RequestBody AgentListReq req) {
        return ApiResponse.ok(agentService.list(req));
    }
}
