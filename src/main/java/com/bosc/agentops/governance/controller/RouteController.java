package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.dto.RouteListReq;
import com.bosc.agentops.governance.dto.RouteSyncReq;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务路由管理：sync（从 RUNNING 部署生成/更新路由）、list、detail。
 */
@RestController
@RequestMapping("/v1/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/sync")
    @RequirePermission("gov:route")
    public ApiResponse<ServiceRoute> sync(@Valid @RequestBody RouteSyncReq req) {
        return ApiResponse.ok(routeService.sync(req));
    }

    @PostMapping("/list")
    @RequirePermission("gov:read")
    public ApiResponse<List<ServiceRoute>> list(@Valid @RequestBody RouteListReq req) {
        return ApiResponse.ok(routeService.list(req));
    }

    @PostMapping("/detail")
    @RequirePermission("gov:read")
    public ApiResponse<ServiceRoute> detail(@Valid @RequestBody GovIdReq req) {
        return ApiResponse.ok(routeService.detail(req));
    }
}
