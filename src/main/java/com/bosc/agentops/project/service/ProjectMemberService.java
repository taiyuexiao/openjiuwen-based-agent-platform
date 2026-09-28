package com.bosc.agentops.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.dto.MemberAddReq;
import com.bosc.agentops.project.dto.MemberChangeRoleReq;
import com.bosc.agentops.project.dto.MemberRemoveReq;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.entity.ProjectMember;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.SubjectType;
import com.bosc.agentops.project.mapper.ProjectMemberMapper;
import com.bosc.agentops.project.mapper.UserGroupMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProjectMemberService {

    private final ProjectMemberMapper projectMemberMapper;
    private final UserGroupMapper userGroupMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public ProjectMemberService(ProjectMemberMapper projectMemberMapper,
                                UserGroupMapper userGroupMapper,
                                ProjectService projectService,
                                AuditService auditService) {
        this.projectMemberMapper = projectMemberMapper;
        this.userGroupMapper = userGroupMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectMember add(Long projectId, MemberAddReq req) {
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        if (req.getSubjectType() == SubjectType.GROUP && userGroupMapper.selectById(toLong(req.getSubjectId())) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户组不存在: " + req.getSubjectId());
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setSubjectType(req.getSubjectType());
        member.setSubjectId(req.getSubjectId());
        member.setRole(req.getRole());
        member.setCreatedBy(RequestContext.currentUserId());
        try {
            projectMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "成员已存在: " + req.getSubjectType() + "/" + req.getSubjectId());
        }
        auditService.record("project", "member:add", "project", projectId,
                Map.of("subjectType", req.getSubjectType().name(), "subjectId", req.getSubjectId(),
                        "role", req.getRole().name()));
        return member;
    }

    @Transactional
    public void remove(Long projectId, MemberRemoveReq req) {
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        ProjectMember member = findMember(projectId, req.getSubjectType(), req.getSubjectId());
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成员不存在: " + req.getSubjectType() + "/" + req.getSubjectId());
        }
        requireNotLastOwner(projectId, member.getRole());
        projectMemberMapper.deleteById(member.getId());
        auditService.record("project", "member:remove", "project", projectId,
                Map.of("subjectType", req.getSubjectType().name(), "subjectId", req.getSubjectId()));
    }

    @Transactional
    public ProjectMember changeRole(Long projectId, MemberChangeRoleReq req) {
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        ProjectMember member = findMember(projectId, req.getSubjectType(), req.getSubjectId());
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "成员不存在: " + req.getSubjectType() + "/" + req.getSubjectId());
        }
        if (member.getRole() == Role.OWNER && req.getRole() != Role.OWNER) {
            requireNotLastOwner(projectId, Role.OWNER);
        }
        member.setRole(req.getRole());
        projectMemberMapper.updateById(member);
        auditService.record("project", "member:change-role", "project", projectId,
                Map.of("subjectType", req.getSubjectType().name(), "subjectId", req.getSubjectId(),
                        "role", req.getRole().name()));
        return member;
    }

    public List<ProjectMember> list(Long projectId) {
        projectService.getOrThrow(projectId);
        return projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
    }

    /** OWNER 不可被移除/降级到没有：项目至少保留一个 OWNER 成员记录 */
    private void requireNotLastOwner(Long projectId, Role removedRole) {
        if (removedRole != Role.OWNER) {
            return;
        }
        Long ownerCount = projectMemberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getRole, Role.OWNER));
        if (ownerCount <= 1) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "项目至少保留一个 OWNER，禁止移除/降级最后一个 OWNER");
        }
    }

    private ProjectMember findMember(Long projectId, SubjectType subjectType, String subjectId) {
        return projectMemberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getSubjectType, subjectType)
                .eq(ProjectMember::getSubjectId, subjectId));
    }

    private Long toLong(String subjectId) {
        try {
            return Long.parseLong(subjectId);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
