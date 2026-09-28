package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.observability.dto.ComponentHealthSummary;
import com.bosc.agentops.observability.dto.ComponentListReq;
import com.bosc.agentops.observability.dto.ComponentRegisterReq;
import com.bosc.agentops.observability.dto.ComponentUpdateReq;
import com.bosc.agentops.observability.dto.ProjectScopedReq;
import com.bosc.agentops.observability.entity.ComponentRegistry;
import com.bosc.agentops.observability.service.ComponentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组件运维：register/update/list/health（健康聚合）。全部要求 obs:component 权限。
 */
@RestController
@RequestMapping("/v1/api/observability/components")
public class ComponentController {

    private final ComponentService componentService;

    public ComponentController(ComponentService componentService) {
        this.componentService = componentService;
    }

    @PostMapping("/register")
    @RequirePermission("obs:component")
    public ApiResponse<ComponentRegistry> register(@Valid @RequestBody ComponentRegisterReq req) {
        return ApiResponse.ok(componentService.register(req));
    }

    @PostMapping("/update")
    @RequirePermission("obs:component")
    public ApiResponse<ComponentRegistry> update(@Valid @RequestBody ComponentUpdateReq req) {
        return ApiResponse.ok(componentService.update(req));
    }

    @PostMapping("/list")
    @RequirePermission("obs:component")
    public ApiResponse<List<ComponentRegistry>> list(@Valid @RequestBody ComponentListReq req) {
        return ApiResponse.ok(componentService.list(req));
    }

    @PostMapping("/health")
    @RequirePermission("obs:component")
    public ApiResponse<ComponentHealthSummary> health(@Valid @RequestBody ProjectScopedReq req) {
        return ApiResponse.ok(componentService.health());
    }
}
