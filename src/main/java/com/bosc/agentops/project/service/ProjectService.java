package com.bosc.agentops.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.dto.ProjectCreateReq;
import com.bosc.agentops.project.dto.ProjectUpdateReq;
import com.bosc.agentops.project.entity.EnvType;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.entity.ProjectEnvironment;
import com.bosc.agentops.project.entity.ProjectMember;
import com.bosc.agentops.project.entity.ProjectStatus;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.SubjectType;
import com.bosc.agentops.project.entity.UserGroupMember;
import com.bosc.agentops.project.mapper.ProjectEnvironmentMapper;
import com.bosc.agentops.project.mapper.ProjectMapper;
import com.bosc.agentops.project.mapper.ProjectMemberMapper;
import com.bosc.agentops.project.mapper.UserGroupMemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectEnvironmentMapper projectEnvironmentMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final AuditService auditService;

    public ProjectService(ProjectMapper projectMapper,
                          ProjectMemberMapper projectMemberMapper,
                          ProjectEnvironmentMapper projectEnvironmentMapper,
                          UserGroupMemberMapper userGroupMemberMapper,
                          AuditService auditService) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectEnvironmentMapper = projectEnvironmentMapper;
        this.userGroupMemberMapper = userGroupMemberMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Project create(ProjectCreateReq req) {
        String userId = RequestContext.currentUserId();
        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getCode, req.getCode()));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "项目 code 已存在: " + req.getCode());
        }
        Project project = new Project();
        project.setCode(req.getCode());
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setOwnerId(userId);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedBy(userId);
        try {
            projectMapper.insert(project);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "项目 code 已存在: " + req.getCode());
        }

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setSubjectType(SubjectType.USER);
        owner.setSubjectId(userId);
        owner.setRole(Role.OWNER);
        owner.setCreatedBy(userId);
        projectMemberMapper.insert(owner);

        for (EnvType env : EnvType.values()) {
            ProjectEnvironment environment = new ProjectEnvironment();
            environment.setProjectId(project.getId());
            environment.setEnv(env);
            projectEnvironmentMapper.insert(environment);
        }

        auditService.record("project", "create", "project", project.getId(),
                Map.of("code", project.getCode(), "name", project.getName()));
        return project;
    }

    @Transactional
    public Project update(ProjectUpdateReq req) {
        Project project = getOrThrow(req.getId());
        requireActive(project);
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        projectMapper.updateById(project);
        auditService.record("project", "update", "project", project.getId(),
                Map.of("name", project.getName()));
        return project;
    }

    @Transactional
    public Project archive(Long id) {
        Project project = getOrThrow(id);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "项目已是归档状态");
        }
        project.setStatus(ProjectStatus.ARCHIVED);
        projectMapper.updateById(project);
        auditService.record("project", "archive", "project", project.getId(), null);
        return project;
    }

    public Project detail(Long id) {
        return getOrThrow(id);
    }

    /** 只返回调用人有权限（为成员，含组授权）的项目 */
    public List<Project> listMine() {
        String userId = RequestContext.currentUserId();
        List<Long> groupIds = userGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<UserGroupMember>().eq(UserGroupMember::getUserId, userId))
                .stream().map(UserGroupMember::getGroupId).toList();
        // 组为空时用不可能命中的哨兵值，保证 GROUP 分支不可满足
        List<String> groupIdStrs = groupIds.isEmpty()
                ? List.of("__no_group__")
                : groupIds.stream().map(String::valueOf).toList();

        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<ProjectMember>()
                .select(ProjectMember::getProjectId)
                .and(w -> w
                        .and(u -> u.eq(ProjectMember::getSubjectType, SubjectType.USER)
                                .eq(ProjectMember::getSubjectId, userId))
                        .or(g -> g.eq(ProjectMember::getSubjectType, SubjectType.GROUP)
                                .in(ProjectMember::getSubjectId, groupIdStrs)));
        List<Long> projectIds = projectMemberMapper.selectList(wrapper).stream()
                .map(ProjectMember::getProjectId).distinct().toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>().in(Project::getId, projectIds));
    }

    public Project getOrThrow(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目不存在: " + id);
        }
        return project;
    }

    public void requireActive(Project project) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "项目已归档，禁止写操作: " + project.getId());
        }
    }
}
