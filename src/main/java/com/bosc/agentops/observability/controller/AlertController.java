package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.observability.dto.AlertHandleReq;
import com.bosc.agentops.observability.dto.AlertListReq;
import com.bosc.agentops.observability.dto.ObsIdReq;
import com.bosc.agentops.observability.entity.AlertEvent;
import com.bosc.agentops.observability.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警管理：list/detail（obs:read）、handle 登记处置（obs:alert）。
 */
@RestController
@RequestMapping("/v1/api/observability/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/list")
    @RequirePermission("obs:read")
    public ApiResponse<List<AlertEvent>> list(@Valid @RequestBody AlertListReq req) {
        return ApiResponse.ok(alertService.list(req));
    }

    @PostMapping("/detail")
    @RequirePermission("obs:read")
    public ApiResponse<AlertEvent> detail(@Valid @RequestBody ObsIdReq req) {
        return ApiResponse.ok(alertService.detail(req));
    }

    @PostMapping("/handle")
    @RequirePermission("obs:alert")
    public ApiResponse<AlertEvent> handle(@Valid @RequestBody AlertHandleReq req) {
        return ApiResponse.ok(alertService.handle(req));
    }
}
