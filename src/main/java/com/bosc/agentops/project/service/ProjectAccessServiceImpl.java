package com.bosc.agentops.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.project.entity.ProjectMember;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.RolePermission;
import com.bosc.agentops.project.entity.SubjectType;
import com.bosc.agentops.project.entity.UserGroupMember;
import com.bosc.agentops.project.mapper.ProjectMemberMapper;
import com.bosc.agentops.project.mapper.RolePermissionMapper;
import com.bosc.agentops.project.mapper.UserGroupMemberMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private final ProjectMemberMapper projectMemberMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public ProjectAccessServiceImpl(ProjectMemberMapper projectMemberMapper,
                                    UserGroupMemberMapper userGroupMemberMapper,
                                    RolePermissionMapper rolePermissionMapper) {
        this.projectMemberMapper = projectMemberMapper;
        this.userGroupMemberMapper = userGroupMemberMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public boolean isMember(String userId, Long projectId) {
        return !rolesOf(userId, projectId).isEmpty();
    }

    @Override
    public Role roleOf(String userId, Long projectId) {
        return Role.highest(rolesOf(userId, projectId));
    }

    @Override
    public boolean hasPermission(String userId, Long projectId, String permission) {
        Set<Role> roles = rolesOf(userId, projectId);
        if (roles.isEmpty()) {
            return false;
        }
        List<RolePermission> mappings = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRole, roles));
        return mappings.stream().anyMatch(rp -> rp.getPermission().equals(permission));
    }

    /** 用户在项目中的全部角色：成员角色直授 ∪ 用户所在组的组授权 */
    public Set<Role> rolesOf(String userId, Long projectId) {
        Set<Role> roles = new HashSet<>();
        if (userId == null || projectId == null) {
            return roles;
        }
        List<Long> groupIds = userGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<UserGroupMember>().eq(UserGroupMember::getUserId, userId))
                .stream().map(UserGroupMember::getGroupId).toList();
        // 组为空时用不可能命中的哨兵值，保证 GROUP 分支不可满足（否则任何组授权都会误命中该用户）
        List<String> groupIdStrs = groupIds.isEmpty()
                ? List.of("__no_group__")
                : groupIds.stream().map(String::valueOf).toList();

        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .and(w -> w
                        .and(u -> u.eq(ProjectMember::getSubjectType, SubjectType.USER)
                                .eq(ProjectMember::getSubjectId, userId))
                        .or(g -> g.eq(ProjectMember::getSubjectType, SubjectType.GROUP)
                                .in(ProjectMember::getSubjectId, groupIdStrs)));
        for (ProjectMember member : projectMemberMapper.selectList(wrapper)) {
            roles.add(member.getRole());
        }
        return roles;
    }
}
