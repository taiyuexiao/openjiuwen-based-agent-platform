package com.bosc.agentops.common.permission.dto;

import java.util.List;

/** 权限矩阵：全部权限点 × 四角色的勾选关系。 */
public class PermissionMatrixResp {

    /** 全部已注册权限点（字典序） */
    private List<String> permissions;
    /** 角色顺序固定 OWNER/ADMIN/DEVELOPER/OPERATOR */
    private List<RolePermissions> roles;

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<RolePermissions> getRoles() {
        return roles;
    }

    public void setRoles(List<RolePermissions> roles) {
        this.roles = roles;
    }

    public static class RolePermissions {
        private String role;
        /** 该角色已勾选的权限点（字典序） */
        private List<String> permissions;

        public RolePermissions() {
        }

        public RolePermissions(String role, List<String> permissions) {
            this.role = role;
            this.permissions = permissions;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }
    }
}
