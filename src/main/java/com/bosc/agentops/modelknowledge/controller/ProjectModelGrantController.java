package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.EffectiveModel;
import com.bosc.agentops.modelknowledge.dto.ModelGrantReq;
import com.bosc.agentops.modelknowledge.dto.ModelGrantRevokeReq;
import com.bosc.agentops.modelknowledge.entity.ProjectModelGrant;
import com.bosc.agentops.modelknowledge.service.ModelAccessService;
import com.bosc.agentops.modelknowledge.service.ProjectModelGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目级模型授权。scope 从路径变量 projectId 解析（参数名约定）。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/model-grants")
public class ProjectModelGrantController {

    private final ProjectModelGrantService projectModelGrantService;
    private final ModelAccessService modelAccessService;

    public ProjectModelGrantController(ProjectModelGrantService projectModelGrantService,
                                       ModelAccessService modelAccessService) {
        this.projectModelGrantService = projectModelGrantService;
        this.modelAccessService = modelAccessService;
    }

    @PostMapping("/grant")
    @RequirePermission("model:grant")
    public ApiResponse<ProjectModelGrant> grant(@PathVariable Long projectId,
                                                @Valid @RequestBody ModelGrantReq req) {
        return ApiResponse.ok(projectModelGrantService.grant(projectId, req));
    }

    @PostMapping("/revoke")
    @RequirePermission("model:grant")
    public ApiResponse<Void> revoke(@PathVariable Long projectId,
                                    @Valid @RequestBody ModelGrantRevokeReq req) {
        projectModelGrantService.revoke(projectId, req.getModelServiceId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("model:read")
    public ApiResponse<List<ProjectModelGrant>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(projectModelGrantService.list(projectId));
    }

    /** 项目当前可用模型 + 参数策略合并结果，供 SDK/底座拉取 */
    @PostMapping("/effective-models")
    @RequirePermission("model:read")
    public ApiResponse<List<EffectiveModel>> effectiveModels(@PathVariable Long projectId) {
        return ApiResponse.ok(modelAccessService.effectiveModels(projectId));
    }
}
