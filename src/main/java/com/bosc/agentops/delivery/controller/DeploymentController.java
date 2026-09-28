package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.DeploymentListReq;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署实例查询与停止。
 */
@RestController
@RequestMapping("/v1/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping("/detail")
    @RequirePermission("delivery:read")
    public ApiResponse<Deployment> detail(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(deploymentService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("delivery:read")
    public ApiResponse<List<Deployment>> list(@Valid @RequestBody DeploymentListReq req) {
        return ApiResponse.ok(deploymentService.list(req));
    }

    @PostMapping("/stop")
    @RequirePermission("delivery:deploy")
    public ApiResponse<Deployment> stop(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(deploymentService.stop(req));
    }
}
