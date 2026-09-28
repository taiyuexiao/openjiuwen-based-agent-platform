package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.observability.dto.AlertWebhookReq;
import com.bosc.agentops.observability.entity.AlertEvent;
import com.bosc.agentops.observability.service.AlertService;
import com.bosc.agentops.observability.support.MachineTokenGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行内告警平台推送入口（机器通道）：X-Platform-Token 校验，不走管理面权限注解。
 */
@RestController
public class AlertWebhookController {

    private final MachineTokenGuard machineTokenGuard;
    private final AlertService alertService;

    public AlertWebhookController(MachineTokenGuard machineTokenGuard, AlertService alertService) {
        this.machineTokenGuard = machineTokenGuard;
        this.alertService = alertService;
    }

    @PostMapping("/v1/alerts/webhook")
    public ApiResponse<AlertEvent> webhook(
            @RequestHeader(value = MachineTokenGuard.TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody AlertWebhookReq req) {
        machineTokenGuard.check(token);
        return ApiResponse.ok(alertService.ingest(req));
    }
}
