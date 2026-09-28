package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.governance.dto.CallerPolicyGrantReq;
import com.bosc.agentops.governance.dto.CallerPolicyListReq;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.service.CallerPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 调用方策略管理：grant（授权 + 流控参数）、revoke、list。
 */
@RestController
@RequestMapping("/v1/api/caller-policies")
public class CallerPolicyController {

    private final CallerPolicyService callerPolicyService;

    public CallerPolicyController(CallerPolicyService callerPolicyService) {
        this.callerPolicyService = callerPolicyService;
    }

    @PostMapping("/grant")
    @RequirePermission("gov:policy")
    public ApiResponse<CallerPolicy> grant(@Valid @RequestBody CallerPolicyGrantReq req) {
        return ApiResponse.ok(callerPolicyService.grant(req));
    }

    @PostMapping("/revoke")
    @RequirePermission("gov:policy")
    public ApiResponse<CallerPolicy> revoke(@Valid @RequestBody GovIdReq req) {
        return ApiResponse.ok(callerPolicyService.revoke(req));
    }

    @PostMapping("/list")
    @RequirePermission("gov:read")
    public ApiResponse<List<CallerPolicy>> list(@Valid @RequestBody CallerPolicyListReq req) {
        return ApiResponse.ok(callerPolicyService.list(req));
    }
}
