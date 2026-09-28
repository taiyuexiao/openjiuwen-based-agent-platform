package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.ModelGrantReq;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.ModelService;
import com.bosc.agentops.modelknowledge.entity.ProjectModelGrant;
import com.bosc.agentops.modelknowledge.mapper.ProjectModelGrantMapper;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 项目级模型授权（grant/revoke/list）。
 * grant 幂等：重复授权视为更新 param_policy（参数策略在授权时冻结范围，改策略即重新授权）。
 */
@Service
public class ProjectModelGrantService {

    private final ProjectModelGrantMapper projectModelGrantMapper;
    private final ModelCatalogService modelCatalogService;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProjectModelGrantService(ProjectModelGrantMapper projectModelGrantMapper,
                                    ModelCatalogService modelCatalogService,
                                    ProjectService projectService,
                                    AuditService auditService,
                                    ObjectMapper objectMapper) {
        this.projectModelGrantMapper = projectModelGrantMapper;
        this.modelCatalogService = modelCatalogService;
        this.projectService = projectService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectModelGrant grant(Long projectId, ModelGrantReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        ModelService modelService = modelCatalogService.getModelServiceOrThrow(req.getModelServiceId());
        if (modelService.getStatus() != CatalogStatus.ENABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "模型已停用，不能授权: " + req.getModelServiceId());
        }
        String paramPolicy = JsonSupport.toJson(objectMapper, req.getParamPolicy());

        ProjectModelGrant grant = projectModelGrantMapper.selectOne(
                new LambdaQueryWrapper<ProjectModelGrant>()
                        .eq(ProjectModelGrant::getProjectId, projectId)
                        .eq(ProjectModelGrant::getModelServiceId, req.getModelServiceId()));
        if (grant == null) {
            grant = new ProjectModelGrant();
            grant.setProjectId(projectId);
            grant.setModelServiceId(req.getModelServiceId());
            grant.setParamPolicy(paramPolicy);
            grant.setGrantedBy(userId);
            projectModelGrantMapper.insert(grant);
        } else {
            grant.setParamPolicy(paramPolicy);
            grant.setGrantedBy(userId);
            projectModelGrantMapper.updateById(grant);
        }
        auditService.record(ModelCatalogService.MODULE, "model.grant", "project_model_grant", grant.getId(),
                Map.of("projectId", projectId, "modelServiceId", req.getModelServiceId()));
        return grant;
    }

    @Transactional
    public void revoke(Long projectId, Long modelServiceId) {
        projectService.getOrThrow(projectId);
        ProjectModelGrant grant = projectModelGrantMapper.selectOne(
                new LambdaQueryWrapper<ProjectModelGrant>()
                        .eq(ProjectModelGrant::getProjectId, projectId)
                        .eq(ProjectModelGrant::getModelServiceId, modelServiceId));
        if (grant == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "授权不存在: projectId=" + projectId + ", modelServiceId=" + modelServiceId);
        }
        projectModelGrantMapper.deleteById(grant.getId());
        auditService.record(ModelCatalogService.MODULE, "model.revoke", "project_model_grant", grant.getId(),
                Map.of("projectId", projectId, "modelServiceId", modelServiceId));
    }

    public List<ProjectModelGrant> list(Long projectId) {
        projectService.getOrThrow(projectId);
        return projectModelGrantMapper.selectList(new LambdaQueryWrapper<ProjectModelGrant>()
                .eq(ProjectModelGrant::getProjectId, projectId)
                .orderByAsc(ProjectModelGrant::getId));
    }
}
