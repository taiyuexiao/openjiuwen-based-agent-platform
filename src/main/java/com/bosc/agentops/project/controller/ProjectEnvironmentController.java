package com.bosc.agentops.project.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.project.dto.EnvQuotaSetReq;
import com.bosc.agentops.project.entity.ProjectEnvironment;
import com.bosc.agentops.project.service.ProjectEnvironmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目研发环境。资源配额本期仅建模存储。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/envs")
public class ProjectEnvironmentController {

    private final ProjectEnvironmentService projectEnvironmentService;

    public ProjectEnvironmentController(ProjectEnvironmentService projectEnvironmentService) {
        this.projectEnvironmentService = projectEnvironmentService;
    }

    @PostMapping("/set-quota")
    @RequirePermission("project:env:manage")
    public ApiResponse<ProjectEnvironment> setQuota(@PathVariable Long projectId,
                                                    @Valid @RequestBody EnvQuotaSetReq req) {
        return ApiResponse.ok(projectEnvironmentService.setQuota(projectId, req));
    }

    @PostMapping("/list")
    @RequirePermission("project:read")
    public ApiResponse<List<ProjectEnvironment>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(projectEnvironmentService.list(projectId));
    }
}
