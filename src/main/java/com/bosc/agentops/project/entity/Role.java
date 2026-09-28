package com.bosc.agentops.project.entity;

/**
 * 项目角色四档。序号越小权限越大，用于多角色取最高档。
 */
public enum Role {
    OWNER,
    ADMIN,
    DEVELOPER,
    OPERATOR;

    public static Role highest(Iterable<Role> roles) {
        Role highest = null;
        for (Role role : roles) {
            if (highest == null || role.ordinal() < highest.ordinal()) {
                highest = role;
            }
        }
        return highest;
    }
}
