package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.ReferenceAddReq;
import com.bosc.agentops.assethub.dto.ReferenceRemoveReq;
import com.bosc.agentops.assethub.entity.AssetReference;
import com.bosc.agentops.assethub.service.AssetReferenceService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 资产引用登记。projectId 为消费方项目；add 前置校验 checkUsable。 */
@RestController
@RequestMapping("/v1/api/assets/{id}/references")
public class AssetReferenceController {

    private final AssetReferenceService assetReferenceService;

    public AssetReferenceController(AssetReferenceService assetReferenceService) {
        this.assetReferenceService = assetReferenceService;
    }

    @PostMapping("/add")
    @RequirePermission("asset:reference")
    public ApiResponse<AssetReference> add(@PathVariable Long id,
                                           @Valid @RequestBody ReferenceAddReq req) {
        return ApiResponse.ok(assetReferenceService.add(id, req));
    }

    @PostMapping("/remove")
    @RequirePermission("asset:reference")
    public ApiResponse<Void> remove(@PathVariable Long id,
                                    @Valid @RequestBody ReferenceRemoveReq req) {
        assetReferenceService.remove(id, req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("asset:read")
    public ApiResponse<List<AssetReference>> list(@PathVariable Long id,
                                                  @Valid @RequestBody ProjectScopedReq req) {
        return ApiResponse.ok(assetReferenceService.list(id, req));
    }
}
