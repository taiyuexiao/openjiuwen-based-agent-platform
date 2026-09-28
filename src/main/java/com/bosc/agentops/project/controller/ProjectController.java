package com.bosc.agentops.project.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.PermissionScope;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.project.dto.ProjectCreateReq;
import com.bosc.agentops.project.dto.ProjectIdReq;
import com.bosc.agentops.project.dto.ProjectUpdateReq;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** 平台级权限：本期简化为所有认证用户可创建，创建者自动成为 OWNER */
    @PostMapping("/create")
    @RequirePermission(value = "project:create", projectScoped = false)
    public ApiResponse<Project> create(@Valid @RequestBody ProjectCreateReq req) {
        return ApiResponse.ok(projectService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission("project:update")
    public ApiResponse<Project> update(@PermissionScope("#req.id") @Valid @RequestBody ProjectUpdateReq req) {
        return ApiResponse.ok(projectService.update(req));
    }

    @PostMapping("/archive")
    @RequirePermission("project:archive")
    public ApiResponse<Project> archive(@PermissionScope("#req.id") @Valid @RequestBody ProjectIdReq req) {
        return ApiResponse.ok(projectService.archive(req.getId()));
    }

    @PostMapping("/detail")
    @RequirePermission("project:read")
    public ApiResponse<Project> detail(@PermissionScope("#req.id") @Valid @RequestBody ProjectIdReq req) {
        return ApiResponse.ok(projectService.detail(req.getId()));
    }

    /** 只返回调用人有权限的项目 */
    @PostMapping("/list")
    @RequirePermission(value = "project:list", projectScoped = false)
    public ApiResponse<List<Project>> list() {
        return ApiResponse.ok(projectService.listMine());
    }
}
