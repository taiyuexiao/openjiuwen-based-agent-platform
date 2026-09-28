package com.bosc.agentops.project.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.project.dto.GroupCreateReq;
import com.bosc.agentops.project.dto.GroupIdReq;
import com.bosc.agentops.project.dto.GroupMemberReq;
import com.bosc.agentops.project.dto.GroupUpdateReq;
import com.bosc.agentops.project.entity.UserGroup;
import com.bosc.agentops.project.entity.UserGroupMember;
import com.bosc.agentops.project.service.UserGroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户组。本期简化：认证用户可建组，仅组创建者可改/删组、加人/移人（service 层校验）。
 */
@RestController
@RequestMapping("/v1/api/user-groups")
public class UserGroupController {

    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @PostMapping("/create")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<UserGroup> create(@Valid @RequestBody GroupCreateReq req) {
        return ApiResponse.ok(userGroupService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<UserGroup> update(@Valid @RequestBody GroupUpdateReq req) {
        return ApiResponse.ok(userGroupService.update(req));
    }

    @PostMapping("/delete")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<Void> delete(@Valid @RequestBody GroupIdReq req) {
        userGroupService.delete(req.getId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<List<UserGroup>> list() {
        return ApiResponse.ok(userGroupService.list());
    }

    @PostMapping("/members/add")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<UserGroupMember> addMember(@Valid @RequestBody GroupMemberReq req) {
        return ApiResponse.ok(userGroupService.addMember(req.getGroupId(), req.getUserId()));
    }

    @PostMapping("/members/remove")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<Void> removeMember(@Valid @RequestBody GroupMemberReq req) {
        userGroupService.removeMember(req.getGroupId(), req.getUserId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/members/list")
    @RequirePermission(value = "group:manage", projectScoped = false)
    public ApiResponse<List<UserGroupMember>> listMembers(@Valid @RequestBody GroupIdReq req) {
        return ApiResponse.ok(userGroupService.listMembers(req.getId()));
    }
}
