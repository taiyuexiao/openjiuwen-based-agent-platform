package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.CatalogIdReq;
import com.bosc.agentops.modelknowledge.dto.ModelServiceCreateReq;
import com.bosc.agentops.modelknowledge.dto.ModelServiceListReq;
import com.bosc.agentops.modelknowledge.dto.ModelServiceUpdateReq;
import com.bosc.agentops.modelknowledge.entity.ModelService;
import com.bosc.agentops.modelknowledge.service.ModelCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型服务目录。
 * create/update/disable 为平台级目录管理：本期约定「认证用户 + 记录操作人」（与 project:create 同级，
 * PermissionService 对平台级权限放行），后续接平台运营角色收紧。
 */
@RestController
@RequestMapping("/v1/api/model-services")
public class ModelServiceController {

    private final ModelCatalogService modelCatalogService;

    public ModelServiceController(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    @PostMapping("/create")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelService> create(@Valid @RequestBody ModelServiceCreateReq req) {
        return ApiResponse.ok(modelCatalogService.createModelService(req));
    }

    @PostMapping("/update")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelService> update(@Valid @RequestBody ModelServiceUpdateReq req) {
        return ApiResponse.ok(modelCatalogService.updateModelService(req));
    }

    @PostMapping("/disable")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelService> disable(@Valid @RequestBody CatalogIdReq req) {
        return ApiResponse.ok(modelCatalogService.disableModelService(req.getId()));
    }

    @PostMapping("/list")
    @RequirePermission(value = "model:read", projectScoped = false)
    public ApiResponse<List<ModelService>> list(@RequestBody(required = false) ModelServiceListReq req) {
        return ApiResponse.ok(modelCatalogService.listModelServices(req == null ? null : req.getProviderId()));
    }
}
