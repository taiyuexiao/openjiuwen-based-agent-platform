package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.observability.dto.DisableRouteReq;
import com.bosc.agentops.observability.dto.RevokeCallerReq;
import com.bosc.agentops.observability.service.OpsActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处置动作：disable-route（路由停流量）/ revoke-caller（吊销调用方）。
 * 全部要求 obs:action 权限且落 module=observability 审计。
 */
@RestController
@RequestMapping("/v1/api/observability/actions")
public class OpsActionController {

    private final OpsActionService opsActionService;

    public OpsActionController(OpsActionService opsActionService) {
        this.opsActionService = opsActionService;
    }

    @PostMapping("/disable-route")
    @RequirePermission("obs:action")
    public ApiResponse<ServiceRoute> disableRoute(@Valid @RequestBody DisableRouteReq req) {
        return ApiResponse.ok(opsActionService.disableRoute(req));
    }

    @PostMapping("/revoke-caller")
    @RequirePermission("obs:action")
    public ApiResponse<CallerPolicy> revokeCaller(@Valid @RequestBody RevokeCallerReq req) {
        return ApiResponse.ok(opsActionService.revokeCaller(req));
    }
}
