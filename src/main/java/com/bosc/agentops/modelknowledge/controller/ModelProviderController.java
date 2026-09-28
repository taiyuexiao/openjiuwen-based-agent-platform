package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.CatalogIdReq;
import com.bosc.agentops.modelknowledge.dto.ModelProviderCreateReq;
import com.bosc.agentops.modelknowledge.dto.ModelProviderUpdateReq;
import com.bosc.agentops.modelknowledge.entity.ModelProvider;
import com.bosc.agentops.modelknowledge.service.ModelCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型 Provider 平台级目录。
 * create/update/disable 为平台级目录管理：本期约定「认证用户 + 记录操作人」（与 project:create 同级，
 * PermissionService 对平台级权限放行），后续接平台运营角色收紧。
 */
@RestController
@RequestMapping("/v1/api/model-providers")
public class ModelProviderController {

    private final ModelCatalogService modelCatalogService;

    public ModelProviderController(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    @PostMapping("/create")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelProvider> create(@Valid @RequestBody ModelProviderCreateReq req) {
        return ApiResponse.ok(modelCatalogService.createProvider(req));
    }

    @PostMapping("/update")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelProvider> update(@Valid @RequestBody ModelProviderUpdateReq req) {
        return ApiResponse.ok(modelCatalogService.updateProvider(req));
    }

    @PostMapping("/disable")
    @RequirePermission(value = "model:manage", projectScoped = false)
    public ApiResponse<ModelProvider> disable(@Valid @RequestBody CatalogIdReq req) {
        return ApiResponse.ok(modelCatalogService.disableProvider(req.getId()));
    }

    @PostMapping("/list")
    @RequirePermission(value = "model:read", projectScoped = false)
    public ApiResponse<List<ModelProvider>> list() {
        return ApiResponse.ok(modelCatalogService.listProviders());
    }
}
