package com.bosc.agentops.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.project.dto.EnvQuotaSetReq;
import com.bosc.agentops.project.entity.EnvType;
import com.bosc.agentops.project.entity.ProjectEnvironment;
import com.bosc.agentops.project.mapper.ProjectEnvironmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProjectEnvironmentService {

    private final ProjectEnvironmentMapper projectEnvironmentMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public ProjectEnvironmentService(ProjectEnvironmentMapper projectEnvironmentMapper,
                                     ProjectService projectService,
                                     AuditService auditService) {
        this.projectEnvironmentMapper = projectEnvironmentMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectEnvironment setQuota(Long projectId, EnvQuotaSetReq req) {
        projectService.requireActive(projectService.getOrThrow(projectId));
        ProjectEnvironment environment = projectEnvironmentMapper.selectOne(
                new LambdaQueryWrapper<ProjectEnvironment>()
                        .eq(ProjectEnvironment::getProjectId, projectId)
                        .eq(ProjectEnvironment::getEnv, req.getEnv()));
        if (environment == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "环境不存在: project=" + projectId + ", env=" + req.getEnv());
        }
        environment.setResourceQuota(req.getResourceQuota());
        projectEnvironmentMapper.updateById(environment);
        auditService.record("project", "env:set-quota", "project_environment", environment.getId(),
                Map.of("projectId", projectId.toString(), "env", req.getEnv().name()));
        return environment;
    }

    public List<ProjectEnvironment> list(Long projectId) {
        projectService.getOrThrow(projectId);
        return projectEnvironmentMapper.selectList(
                new LambdaQueryWrapper<ProjectEnvironment>().eq(ProjectEnvironment::getProjectId, projectId));
    }

    public ProjectEnvironment get(Long projectId, EnvType env) {
        projectService.getOrThrow(projectId);
        ProjectEnvironment environment = projectEnvironmentMapper.selectOne(
                new LambdaQueryWrapper<ProjectEnvironment>()
                        .eq(ProjectEnvironment::getProjectId, projectId)
                        .eq(ProjectEnvironment::getEnv, env));
        if (environment == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "环境不存在: project=" + projectId + ", env=" + env);
        }
        return environment;
    }
}
