package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.ProjectTargetReq;
import com.bosc.agentops.delivery.dto.ProjectTargetResp;
import com.bosc.agentops.delivery.service.DeployTargetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目可选/默认部署目标。scope 从路径变量 projectId 解析（参数名约定）。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/deploy-targets")
public class ProjectDeployTargetController {

    private final DeployTargetService deployTargetService;

    public ProjectDeployTargetController(DeployTargetService deployTargetService) {
        this.deployTargetService = deployTargetService;
    }

    @PostMapping("/attach")
    @RequirePermission("delivery:target")
    public ApiResponse<ProjectTargetResp> attach(@PathVariable Long projectId,
                                                 @Valid @RequestBody ProjectTargetReq req) {
        return ApiResponse.ok(deployTargetService.attach(projectId, req));
    }

    @PostMapping("/detach")
    @RequirePermission("delivery:target")
    public ApiResponse<Void> detach(@PathVariable Long projectId,
                                    @Valid @RequestBody ProjectTargetReq req) {
        deployTargetService.detach(projectId, req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/set-default")
    @RequirePermission("delivery:target")
    public ApiResponse<ProjectTargetResp> setDefault(@PathVariable Long projectId,
                                                     @Valid @RequestBody ProjectTargetReq req) {
        return ApiResponse.ok(deployTargetService.setDefault(projectId, req));
    }

    @PostMapping("/list")
    @RequirePermission("delivery:read")
    public ApiResponse<List<ProjectTargetResp>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(deployTargetService.listProjectTargets(projectId));
    }
}
