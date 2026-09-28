package com.bosc.agentops.common.permission.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.common.permission.dto.PermissionMatrixResp;
import com.bosc.agentops.common.permission.dto.PermissionRoleUpdateReq;
import com.bosc.agentops.common.permission.service.PermissionMatrixService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限矩阵查看与管理（平台级接口）。matrix 认证用户可看；
 * roles/update 在 service 层二次校验平台管理员（agentops.auth.admins）。
 */
@RestController
@RequestMapping("/v1/api/permissions")
public class PermissionMatrixController {

    private final PermissionMatrixService permissionMatrixService;

    public PermissionMatrixController(PermissionMatrixService permissionMatrixService) {
        this.permissionMatrixService = permissionMatrixService;
    }

    @PostMapping("/matrix")
    @RequirePermission(value = "permission:matrix:read", projectScoped = false)
    public ApiResponse<PermissionMatrixResp> matrix() {
        return ApiResponse.ok(permissionMatrixService.matrix());
    }

    @PostMapping("/roles/update")
    @RequirePermission(value = "permission:role:update", projectScoped = false)
    public ApiResponse<Void> updateRole(@Valid @RequestBody PermissionRoleUpdateReq req) {
        permissionMatrixService.updateRole(req);
        return ApiResponse.ok(null);
    }
}
