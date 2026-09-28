package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.dto.ReleaseApproveReq;
import com.bosc.agentops.delivery.dto.ReleaseCreateReq;
import com.bosc.agentops.delivery.dto.ReleaseListReq;
import com.bosc.agentops.delivery.entity.Release;
import com.bosc.agentops.delivery.service.ReleaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发布流程：create/gate/approve/deploy/rollback + detail/list。
 * approve 权限点独立于 release（拟办分离）：拟Release的人不能批准自己的Release。
 */
@RestController
@RequestMapping("/v1/api/releases")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @PostMapping("/create")
    @RequirePermission("delivery:release")
    public ApiResponse<Release> create(@Valid @RequestBody ReleaseCreateReq req) {
        return ApiResponse.ok(releaseService.create(req));
    }

    @PostMapping("/gate")
    @RequirePermission("delivery:release")
    public ApiResponse<Release> gate(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(releaseService.gate(req));
    }

    @PostMapping("/approve")
    @RequirePermission("delivery:approve")
    public ApiResponse<Release> approve(@Valid @RequestBody ReleaseApproveReq req) {
        return ApiResponse.ok(releaseService.approve(req));
    }

    @PostMapping("/deploy")
    @RequirePermission("delivery:deploy")
    public ApiResponse<Release> deploy(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(releaseService.deploy(req));
    }

    @PostMapping("/rollback")
    @RequirePermission("delivery:release")
    public ApiResponse<Release> rollback(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(releaseService.rollback(req));
    }

    @PostMapping("/detail")
    @RequirePermission("delivery:read")
    public ApiResponse<Release> detail(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(releaseService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("delivery:read")
    public ApiResponse<List<Release>> list(@Valid @RequestBody ReleaseListReq req) {
        return ApiResponse.ok(releaseService.list(req));
    }
}
