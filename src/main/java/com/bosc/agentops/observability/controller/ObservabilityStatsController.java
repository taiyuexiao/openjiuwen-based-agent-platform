package com.bosc.agentops.observability.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.observability.dto.StatsOverviewReq;
import com.bosc.agentops.observability.dto.StatsOverviewResp;
import com.bosc.agentops.observability.service.ObservabilityStatsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观测统计聚合。平台级注解 + service 层手工校验项目 obs:read：
 * projectId 为空时按平台级聚合，不走项目 scope 解析。
 */
@RestController
@RequestMapping("/v1/api/observability/stats")
public class ObservabilityStatsController {

    private final ObservabilityStatsService observabilityStatsService;

    public ObservabilityStatsController(ObservabilityStatsService observabilityStatsService) {
        this.observabilityStatsService = observabilityStatsService;
    }

    @PostMapping("/overview")
    @RequirePermission(value = "obs:read", projectScoped = false)
    public ApiResponse<StatsOverviewResp> overview(@RequestBody(required = false) StatsOverviewReq req) {
        return ApiResponse.ok(observabilityStatsService.overview(req));
    }
}
