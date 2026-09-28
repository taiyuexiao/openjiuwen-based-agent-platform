package com.bosc.agentops.common.permission.dto;

import com.bosc.agentops.project.entity.Role;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 角色权限整体替换：permissions 为该角色的完整权限点集合（空列表=清空）。 */
public class PermissionRoleUpdateReq {

    @NotNull
    private Role role;

    @NotNull
    private List<String> permissions;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
