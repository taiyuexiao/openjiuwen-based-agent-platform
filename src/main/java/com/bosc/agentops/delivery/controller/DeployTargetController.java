package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.DeployTargetCreateReq;
import com.bosc.agentops.delivery.dto.DeployTargetListReq;
import com.bosc.agentops.delivery.dto.DeployTargetUpdateReq;
import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.service.DeployTargetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部署目标（平台级资源，projectScoped=false）：create/update/disable/list。
 * 项目侧可选/默认目标见 ProjectDeployTargetController。
 */
@RestController
@RequestMapping("/v1/api/deploy-targets")
public class DeployTargetController {

    private final DeployTargetService deployTargetService;

    public DeployTargetController(DeployTargetService deployTargetService) {
        this.deployTargetService = deployTargetService;
    }

    @PostMapping("/create")
    @RequirePermission(value = "delivery:target", projectScoped = false)
    public ApiResponse<DeployTarget> create(@Valid @RequestBody DeployTargetCreateReq req) {
        return ApiResponse.ok(deployTargetService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission(value = "delivery:target", projectScoped = false)
    public ApiResponse<DeployTarget> update(@Valid @RequestBody DeployTargetUpdateReq req) {
        return ApiResponse.ok(deployTargetService.update(req));
    }

    @PostMapping("/disable")
    @RequirePermission(value = "delivery:target", projectScoped = false)
    public ApiResponse<DeployTarget> disable(@Valid @RequestBody IdReq req) {
        return ApiResponse.ok(deployTargetService.disable(req.getId()));
    }

    @PostMapping("/list")
    @RequirePermission(value = "delivery:read", projectScoped = false)
    public ApiResponse<List<DeployTarget>> list(@Valid @RequestBody DeployTargetListReq req) {
        return ApiResponse.ok(deployTargetService.list(req));
    }

    /** disable 请求体 */
    public static class IdReq {
        @NotNull
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
