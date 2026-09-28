package com.bosc.agentops.project.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.project.dto.MemberAddReq;
import com.bosc.agentops.project.dto.MemberChangeRoleReq;
import com.bosc.agentops.project.dto.MemberRemoveReq;
import com.bosc.agentops.project.entity.ProjectMember;
import com.bosc.agentops.project.service.ProjectMemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目成员管理。scope 从路径变量 projectId 解析（参数名约定）。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @PostMapping("/add")
    @RequirePermission("project:member:manage")
    public ApiResponse<ProjectMember> add(@PathVariable Long projectId,
                                          @Valid @RequestBody MemberAddReq req) {
        return ApiResponse.ok(projectMemberService.add(projectId, req));
    }

    @PostMapping("/remove")
    @RequirePermission("project:member:manage")
    public ApiResponse<Void> remove(@PathVariable Long projectId,
                                    @Valid @RequestBody MemberRemoveReq req) {
        projectMemberService.remove(projectId, req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/change-role")
    @RequirePermission("project:member:manage")
    public ApiResponse<ProjectMember> changeRole(@PathVariable Long projectId,
                                                 @Valid @RequestBody MemberChangeRoleReq req) {
        return ApiResponse.ok(projectMemberService.changeRole(projectId, req));
    }

    @PostMapping("/list")
    @RequirePermission("project:read")
    public ApiResponse<List<ProjectMember>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(projectMemberService.list(projectId));
    }
}
