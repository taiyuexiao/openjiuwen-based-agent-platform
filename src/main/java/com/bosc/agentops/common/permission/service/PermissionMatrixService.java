package com.bosc.agentops.common.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.auth.SimpleTokenAuthProvider;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.common.permission.dto.PermissionMatrixResp;
import com.bosc.agentops.common.permission.dto.PermissionRoleUpdateReq;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.RolePermission;
import com.bosc.agentops.project.mapper.RolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 权限矩阵查看与管理。矩阵数据来自 role_permission 表（项目域实体，此处只读/受控替换）。
 * roles/update 仅平台管理员（agentops.auth.admins）可用；OWNER 角色拒绝修改（防锁死）。
 */
@Service
public class PermissionMatrixService {

    static final String MODULE = "foundation";

    private final RolePermissionMapper rolePermissionMapper;
    private final SimpleTokenAuthProvider authProvider;
    private final AuditService auditService;

    public PermissionMatrixService(RolePermissionMapper rolePermissionMapper,
                                   SimpleTokenAuthProvider authProvider,
                                   AuditService auditService) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.authProvider = authProvider;
        this.auditService = auditService;
    }

    public PermissionMatrixResp matrix() {
        List<RolePermission> all = rolePermissionMapper.selectList(null);
        PermissionMatrixResp resp = new PermissionMatrixResp();
        resp.setPermissions(all.stream().map(RolePermission::getPermission)
                .distinct().sorted().toList());
        List<PermissionMatrixResp.RolePermissions> roles = new ArrayList<>();
        for (Role role : Role.values()) {
            roles.add(new PermissionMatrixResp.RolePermissions(role.name(),
                    all.stream().filter(rp -> rp.getRole() == role)
                            .map(RolePermission::getPermission).sorted().toList()));
        }
        resp.setRoles(roles);
        return resp;
    }

    /** 整体替换某角色的权限点集合。仅平台管理员；OWNER 拒绝修改（40902 防锁死）。 */
    @Transactional
    public void updateRole(PermissionRoleUpdateReq req) {
        String userId = RequestContext.currentUserId();
        if (!authProvider.isAdmin(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台管理员可维护角色权限: " + userId);
        }
        if (req.getRole() == Role.OWNER) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "OWNER 角色权限不允许修改（防锁死）");
        }
        List<String> permissions = new ArrayList<>(new LinkedHashSet<>(req.getPermissions()));
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "权限点不能为空串");
            }
        }
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRole, req.getRole()));
        for (String permission : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(req.getRole());
            rp.setPermission(permission);
            rolePermissionMapper.insert(rp);
        }
        auditService.record(MODULE, "permission.role.update", "role", req.getRole().name(),
                Map.of("role", req.getRole().name(), "permissions", permissions));
    }
}
