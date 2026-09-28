package com.bosc.agentops.enterprise.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.enterprise.dto.BindReq;
import com.bosc.agentops.enterprise.dto.BindingListReq;
import com.bosc.agentops.enterprise.dto.BindingOperateReq;
import com.bosc.agentops.enterprise.dto.BindingResp;
import com.bosc.agentops.enterprise.service.AppBindingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 归属应用绑定。权限 scope 从请求体 projectId 解析；Agent 所属项目一致性在 service 层二次校验。
 */
@RestController
@RequestMapping("/v1/api/bindings")
public class AppBindingController {

    private final AppBindingService bindingService;

    public AppBindingController(AppBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/bind")
    @RequirePermission("binding:manage")
    public ApiResponse<BindingResp> bind(@Valid @RequestBody BindReq req) {
        return ApiResponse.ok(bindingService.bind(req));
    }

    @PostMapping("/resync")
    @RequirePermission("binding:manage")
    public ApiResponse<BindingResp> resync(@Valid @RequestBody BindingOperateReq req) {
        return ApiResponse.ok(bindingService.resync(req));
    }

    @PostMapping("/unbind")
    @RequirePermission("binding:manage")
    public ApiResponse<BindingResp> unbind(@Valid @RequestBody BindingOperateReq req) {
        return ApiResponse.ok(bindingService.unbind(req));
    }

    @PostMapping("/detail")
    @RequirePermission("binding:read")
    public ApiResponse<BindingResp> detail(@Valid @RequestBody BindingOperateReq req) {
        return ApiResponse.ok(bindingService.detail(req));
    }

    @PostMapping("/list")
    @RequirePermission("binding:read")
    public ApiResponse<List<BindingResp>> list(@Valid @RequestBody BindingListReq req) {
        return ApiResponse.ok(bindingService.list(req));
    }
}
