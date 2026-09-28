package com.bosc.agentops.agentdev.controller;

import com.bosc.agentops.agentdev.dto.VersionDetailReq;
import com.bosc.agentops.agentdev.dto.VersionListReq;
import com.bosc.agentops.agentdev.dto.VersionRegisterReq;
import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.agentdev.service.AgentVersionService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 版本：register 执行声明登记校验（逐项校验、失败项一次性返回）；
 * 登记即 REGISTERED 且 declaration 不可变。
 */
@RestController
@RequestMapping("/v1/api/agents/{id}/versions")
public class AgentVersionController {

    private final AgentVersionService agentVersionService;

    public AgentVersionController(AgentVersionService agentVersionService) {
        this.agentVersionService = agentVersionService;
    }

    @PostMapping("/register")
    @RequirePermission("agent:register")
    public ApiResponse<AgentVersion> register(@PathVariable Long id,
                                              @Valid @RequestBody VersionRegisterReq req) {
        return ApiResponse.ok(agentVersionService.register(id, req));
    }

    @PostMapping("/detail")
    @RequirePermission("agent:read")
    public ApiResponse<AgentVersion> detail(@PathVariable Long id,
                                            @Valid @RequestBody VersionDetailReq req) {
        return ApiResponse.ok(agentVersionService.detail(id, req));
    }

    @PostMapping("/list")
    @RequirePermission("agent:read")
    public ApiResponse<List<AgentVersion>> list(@PathVariable Long id,
                                                @Valid @RequestBody VersionListReq req) {
        return ApiResponse.ok(agentVersionService.list(id, req.getProjectId()));
    }
}
