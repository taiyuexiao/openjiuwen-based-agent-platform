package com.bosc.agentops.common.permission;

/**
 * 权限判定入口。项目域实现由模块 01（project）提供。
 * 判定规则：项目成员角色直授 ∪ 用户所在组的组授权；查不到任何授权即拒绝（fail-closed）。
 */
public interface PermissionService {

    /**
     * @param userId     操作人
     * @param permission 权限点，格式 &lt;模块&gt;:&lt;资源&gt;:&lt;动作&gt;
     * @param projectId  项目 scope；平台级权限传 null
     */
    boolean check(String userId, String permission, Long projectId);
}
