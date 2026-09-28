package com.bosc.agentops.delivery.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.delivery.dto.ArtifactListReq;
import com.bosc.agentops.delivery.dto.ArtifactRegisterReq;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.entity.Artifact;
import com.bosc.agentops.delivery.service.ArtifactService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 制品登记与查询。制品不可变：无 update 接口。
 */
@RestController
@RequestMapping("/v1/api/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PostMapping("/register")
    @RequirePermission("delivery:artifact")
    public ApiResponse<Artifact> register(@Valid @RequestBody ArtifactRegisterReq req) {
        return ApiResponse.ok(artifactService.register(req));
    }

    @PostMapping("/detail")
    @RequirePermission("delivery:read")
    public ApiResponse<Artifact> detail(@Valid @RequestBody DeliveryIdReq req) {
        return ApiResponse.ok(artifactService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("delivery:read")
    public ApiResponse<List<Artifact>> list(@Valid @RequestBody ArtifactListReq req) {
        return ApiResponse.ok(artifactService.list(req));
    }
}
