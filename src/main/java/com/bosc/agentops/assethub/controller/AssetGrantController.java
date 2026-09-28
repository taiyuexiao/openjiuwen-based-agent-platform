package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.AssetGrantReq;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.entity.AssetGrant;
import com.bosc.agentops.assethub.service.AssetGrantService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 跨项目共享授权。projectId 为归属项目（操作面），toProjectId 为被授权项目。 */
@RestController
@RequestMapping("/v1/api/assets/{id}/grants")
public class AssetGrantController {

    private final AssetGrantService assetGrantService;

    public AssetGrantController(AssetGrantService assetGrantService) {
        this.assetGrantService = assetGrantService;
    }

    @PostMapping("/grant")
    @RequirePermission("asset:grant")
    public ApiResponse<AssetGrant> grant(@PathVariable Long id,
                                         @Valid @RequestBody AssetGrantReq req) {
        return ApiResponse.ok(assetGrantService.grant(id, req));
    }

    @PostMapping("/revoke")
    @RequirePermission("asset:grant")
    public ApiResponse<Void> revoke(@PathVariable Long id,
                                    @Valid @RequestBody AssetGrantReq req) {
        assetGrantService.revoke(id, req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("asset:read")
    public ApiResponse<List<AssetGrant>> list(@PathVariable Long id,
                                              @Valid @RequestBody ProjectScopedReq req) {
        return ApiResponse.ok(assetGrantService.list(id, req));
    }
}
