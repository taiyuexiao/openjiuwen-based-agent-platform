package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.AssetCreateReq;
import com.bosc.agentops.assethub.dto.AssetListReq;
import com.bosc.agentops.assethub.dto.AssetOperateReq;
import com.bosc.agentops.assethub.dto.AssetUpdateReq;
import com.bosc.agentops.assethub.dto.DraftVersionPublishReq;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.service.AssetService;
import com.bosc.agentops.assethub.service.AssetVersionService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资产 CRUD 与生命周期。权限 scope 从请求体 projectId 解析（getProjectId 约定）；
 * 归属项目一致性、offline 的责任人/ADMIN 二次校验在 service 层。
 */
@RestController
@RequestMapping("/v1/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final AssetVersionService assetVersionService;

    public AssetController(AssetService assetService, AssetVersionService assetVersionService) {
        this.assetService = assetService;
        this.assetVersionService = assetVersionService;
    }

    @PostMapping("/create")
    @RequirePermission("asset:create")
    public ApiResponse<Asset> create(@Valid @RequestBody AssetCreateReq req) {
        return ApiResponse.ok(assetService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission("asset:update")
    public ApiResponse<Asset> update(@Valid @RequestBody AssetUpdateReq req) {
        return ApiResponse.ok(assetService.update(req));
    }

    @PostMapping("/publish")
    @RequirePermission("asset:publish")
    public ApiResponse<Asset> publish(@Valid @RequestBody AssetOperateReq req) {
        return ApiResponse.ok(assetService.publish(req));
    }

    @PostMapping("/versions/publish-draft")
    @RequirePermission("asset:publish")
    public ApiResponse<AssetVersion> publishDraft(@Valid @RequestBody DraftVersionPublishReq req) {
        return ApiResponse.ok(assetVersionService.publishDraft(req));
    }

    @PostMapping("/offline")
    @RequirePermission("asset:offline")
    public ApiResponse<Asset> offline(@Valid @RequestBody AssetOperateReq req) {
        return ApiResponse.ok(assetService.offline(req));
    }

    @PostMapping("/detail")
    @RequirePermission("asset:read")
    public ApiResponse<Asset> detail(@Valid @RequestBody AssetOperateReq req) {
        return ApiResponse.ok(assetService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("asset:read")
    public ApiResponse<List<Asset>> list(@Valid @RequestBody AssetListReq req) {
        return ApiResponse.ok(assetService.list(req));
    }
}
