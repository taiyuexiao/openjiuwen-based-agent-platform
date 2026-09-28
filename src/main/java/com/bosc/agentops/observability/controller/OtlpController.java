package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.observability.dto.OtlpIngestResp;
import com.bosc.agentops.observability.service.TraceIngestService;
import com.bosc.agentops.observability.support.MachineTokenGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTLP 接收端点（机器通道）：X-Platform-Token 校验，不走管理面权限注解
 * （路径 /v1/otlp/** 已在 agentops.permission.audit-exclude-prefixes 排除）。
 */
@RestController
public class OtlpController {

    private final MachineTokenGuard machineTokenGuard;
    private final TraceIngestService traceIngestService;

    public OtlpController(MachineTokenGuard machineTokenGuard, TraceIngestService traceIngestService) {
        this.machineTokenGuard = machineTokenGuard;
        this.traceIngestService = traceIngestService;
    }

    @PostMapping("/v1/otlp/v1/traces")
    public ApiResponse<OtlpIngestResp> ingest(
            @RequestHeader(value = MachineTokenGuard.TOKEN_HEADER, required = false) String token,
            @RequestBody JsonNode body) {
        machineTokenGuard.check(token);
        return ApiResponse.ok(traceIngestService.ingest(body));
    }
}
