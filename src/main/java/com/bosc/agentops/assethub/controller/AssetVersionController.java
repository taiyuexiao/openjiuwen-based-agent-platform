package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.VersionPublishReq;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.service.AssetVersionService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 资产版本：发布（创建即 PUBLISHED 且不可变）与查询。 */
@RestController
@RequestMapping("/v1/api/assets/{id}/versions")
public class AssetVersionController {

    private final AssetVersionService assetVersionService;

    public AssetVersionController(AssetVersionService assetVersionService) {
        this.assetVersionService = assetVersionService;
    }

    @PostMapping("/publish")
    @RequirePermission("asset:publish")
    public ApiResponse<AssetVersion> publish(@PathVariable Long id,
                                             @Valid @RequestBody VersionPublishReq req) {
        return ApiResponse.ok(assetVersionService.publish(id, req));
    }

    @PostMapping("/list")
    @RequirePermission("asset:read")
    public ApiResponse<List<AssetVersion>> list(@PathVariable Long id,
                                                @Valid @RequestBody ProjectScopedReq req) {
        return ApiResponse.ok(assetVersionService.list(id, req));
    }
}
