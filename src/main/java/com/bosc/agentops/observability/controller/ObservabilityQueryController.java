package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.observability.dto.AuditQueryReq;
import com.bosc.agentops.observability.dto.TraceDetailReq;
import com.bosc.agentops.observability.dto.TraceQueryReq;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.service.AuditQueryService;
import com.bosc.agentops.observability.service.TraceQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 观测查询：链路与审计。全部按项目权限过滤 + 返回前兜底脱敏。
 */
@RestController
@RequestMapping("/v1/api/observability")
public class ObservabilityQueryController {

    private final TraceQueryService traceQueryService;
    private final AuditQueryService auditQueryService;

    public ObservabilityQueryController(TraceQueryService traceQueryService,
                                        AuditQueryService auditQueryService) {
        this.traceQueryService = traceQueryService;
        this.auditQueryService = auditQueryService;
    }

    @PostMapping("/traces/query")
    @RequirePermission("obs:read")
    public ApiResponse<List<TraceSpan>> queryTraces(@Valid @RequestBody TraceQueryReq req) {
        return ApiResponse.ok(traceQueryService.query(req));
    }

    @PostMapping("/traces/{traceId}")
    @RequirePermission("obs:read")
    public ApiResponse<List<TraceSpan>> traceDetail(@PathVariable String traceId,
                                                    @Valid @RequestBody TraceDetailReq req) {
        return ApiResponse.ok(traceQueryService.detail(traceId, req));
    }

    @PostMapping("/audit/query")
    @RequirePermission("obs:read")
    public ApiResponse<List<AuditEvent>> queryAudit(@Valid @RequestBody AuditQueryReq req) {
        return ApiResponse.ok(auditQueryService.query(req));
    }
}
