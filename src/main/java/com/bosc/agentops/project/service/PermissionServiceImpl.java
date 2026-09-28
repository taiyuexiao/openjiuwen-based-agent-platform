package com.bosc.agentops.project.service;

import com.bosc.agentops.common.permission.PermissionService;
import org.springframework.stereotype.Service;

/**
 * 项目域权限判定。fail-closed：查不到任何授权即拒绝。
 * 平台级权限（projectId 为 null）本期简化：所有认证用户放行（如 project:create、group:manage）。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final ProjectAccessService projectAccessService;

    public PermissionServiceImpl(ProjectAccessService projectAccessService) {
        this.projectAccessService = projectAccessService;
    }

    @Override
    public boolean check(String userId, String permission, Long projectId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (projectId == null) {
            return true;
        }
        return projectAccessService.hasPermission(userId, projectId, permission);
    }
}
