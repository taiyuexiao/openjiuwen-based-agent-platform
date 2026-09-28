package com.bosc.agentops.project.service;

import com.bosc.agentops.project.entity.Role;

/**
 * 项目访问 SPI，供其他模块依赖：项目存在性 + 成员判定 + 权限判定。
 */
public interface ProjectAccessService {

    /** 用户是否为项目成员（含组授权展开） */
    boolean isMember(String userId, Long projectId);

    /** 用户在项目中的最高角色（直授 ∪ 组授权），非成员返回 null */
    Role roleOf(String userId, Long projectId);

    /** 用户是否拥有项目级权限点 */
    boolean hasPermission(String userId, Long projectId, String permission);
}
