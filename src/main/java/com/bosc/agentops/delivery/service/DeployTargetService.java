package com.bosc.agentops.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.delivery.dto.DeployTargetCreateReq;
import com.bosc.agentops.delivery.dto.DeployTargetListReq;
import com.bosc.agentops.delivery.dto.DeployTargetUpdateReq;
import com.bosc.agentops.delivery.dto.ProjectTargetReq;
import com.bosc.agentops.delivery.dto.ProjectTargetResp;
import com.bosc.agentops.delivery.entity.AgentLevelValue;
import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.DeployTargetStatus;
import com.bosc.agentops.delivery.entity.ProjectDeployTarget;
import com.bosc.agentops.delivery.mapper.DeployTargetMapper;
import com.bosc.agentops.delivery.mapper.ProjectDeployTargetMapper;
import com.bosc.agentops.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部署目标管理：平台级目标 CRUD（create/update/disable/list）+ 项目侧可选/默认目标
 * （attach/detach/setDefault/list）。目标切换通过新 Release 选新 target 完成并留痕（见 ReleaseService）。
 */
@Service
public class DeployTargetService {

    private final DeployTargetMapper targetMapper;
    private final ProjectDeployTargetMapper projectTargetMapper;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public DeployTargetService(DeployTargetMapper targetMapper, ProjectDeployTargetMapper projectTargetMapper,
                               ProjectService projectService, AuditService auditService,
                               ObjectMapper objectMapper) {
        this.targetMapper = targetMapper;
        this.projectTargetMapper = projectTargetMapper;
        this.projectService = projectService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeployTarget create(DeployTargetCreateReq req) {
        String userId = RequestContext.currentUserId();
        validateLevels(req.getAllowedAgentLevels());
        DeployTarget target = new DeployTarget();
        target.setCode(req.getCode());
        target.setName(req.getName());
        target.setEnv(req.getEnv());
        target.setCluster(req.getCluster());
        target.setNamespace(req.getNamespace());
        target.setBaseResource(JsonSupport.toJson(objectMapper, req.getBaseResource()));
        target.setAllowedAgentLevels(JsonSupport.toJson(objectMapper,
                req.getAllowedAgentLevels() == null ? null : objectMapper.valueToTree(req.getAllowedAgentLevels())));
        target.setStatus(DeployTargetStatus.ACTIVE);
        target.setCreatedBy(userId);
        try {
            targetMapper.insert(target);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "部署目标 code 已存在: " + req.getCode());
        }
        auditService.record(ArtifactService.MODULE, "target.create", "delivery_deploy_target", target.getId(),
                Map.of("code", target.getCode(), "env", target.getEnv().name()));
        return target;
    }

    @Transactional
    public DeployTarget update(DeployTargetUpdateReq req) {
        DeployTarget target = getOrThrow(req.getId());
        if (target.getStatus() == DeployTargetStatus.DISABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "部署目标已停用，禁止修改: " + target.getId());
        }
        if (req.getName() != null) {
            target.setName(req.getName());
        }
        if (req.getCluster() != null) {
            target.setCluster(req.getCluster());
        }
        if (req.getNamespace() != null) {
            target.setNamespace(req.getNamespace());
        }
        if (req.getBaseResource() != null) {
            target.setBaseResource(JsonSupport.toJson(objectMapper, req.getBaseResource()));
        }
        if (req.getAllowedAgentLevels() != null) {
            validateLevels(req.getAllowedAgentLevels());
            target.setAllowedAgentLevels(JsonSupport.toJson(objectMapper,
                    objectMapper.valueToTree(req.getAllowedAgentLevels())));
        }
        targetMapper.updateById(target);
        auditService.record(ArtifactService.MODULE, "target.update", "delivery_deploy_target", target.getId(),
                Map.of("code", target.getCode()));
        return target;
    }

    @Transactional
    public DeployTarget disable(Long id) {
        DeployTarget target = getOrThrow(id);
        if (target.getStatus() == DeployTargetStatus.DISABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "部署目标已是停用状态: " + id);
        }
        target.setStatus(DeployTargetStatus.DISABLED);
        targetMapper.updateById(target);
        auditService.record(ArtifactService.MODULE, "target.disable", "delivery_deploy_target", target.getId(),
                Map.of("code", target.getCode()));
        return target;
    }

    public List<DeployTarget> list(DeployTargetListReq req) {
        return targetMapper.selectList(new LambdaQueryWrapper<DeployTarget>()
                .eq(req.getEnv() != null, DeployTarget::getEnv, req.getEnv())
                .eq(req.getStatus() != null, DeployTarget::getStatus, req.getStatus())
                .orderByAsc(DeployTarget::getId));
    }

    // ---------- 项目可选/默认目标 ----------

    @Transactional
    public ProjectTargetResp attach(Long projectId, ProjectTargetReq req) {
        String userId = RequestContext.currentUserId();
        projectService.requireActive(projectService.getOrThrow(projectId));
        DeployTarget target = getOrThrow(req.getTargetId());
        if (target.getStatus() != DeployTargetStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "部署目标非 ACTIVE，禁止挂接: targetId=" + target.getId() + ", status=" + target.getStatus());
        }
        ProjectDeployTarget mapping = new ProjectDeployTarget();
        mapping.setProjectId(projectId);
        mapping.setTargetId(target.getId());
        mapping.setIsDefault(false);
        mapping.setCreatedBy(userId);
        try {
            projectTargetMapper.insert(mapping);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "项目已挂接该部署目标: projectId=" + projectId + ", targetId=" + target.getId());
        }
        auditService.record(ArtifactService.MODULE, "target.attach", "delivery_project_deploy_target",
                mapping.getId(), Map.of("projectId", projectId, "targetId", target.getId()));
        return new ProjectTargetResp(mapping, target);
    }

    @Transactional
    public void detach(Long projectId, ProjectTargetReq req) {
        ProjectDeployTarget mapping = getMappingOrThrow(projectId, req.getTargetId());
        projectTargetMapper.deleteById(mapping.getId());
        auditService.record(ArtifactService.MODULE, "target.detach", "delivery_project_deploy_target",
                mapping.getId(), Map.of("projectId", projectId, "targetId", req.getTargetId()));
    }

    @Transactional
    public ProjectTargetResp setDefault(Long projectId, ProjectTargetReq req) {
        ProjectDeployTarget mapping = getMappingOrThrow(projectId, req.getTargetId());
        projectTargetMapper.update(null, new LambdaUpdateWrapper<ProjectDeployTarget>()
                .eq(ProjectDeployTarget::getProjectId, projectId)
                .eq(ProjectDeployTarget::getIsDefault, true)
                .set(ProjectDeployTarget::getIsDefault, false));
        mapping.setIsDefault(true);
        projectTargetMapper.updateById(mapping);
        auditService.record(ArtifactService.MODULE, "target.setDefault", "delivery_project_deploy_target",
                mapping.getId(), Map.of("projectId", projectId, "targetId", req.getTargetId()));
        return new ProjectTargetResp(mapping, getOrThrow(mapping.getTargetId()));
    }

    public List<ProjectTargetResp> listProjectTargets(Long projectId) {
        projectService.getOrThrow(projectId);
        List<ProjectDeployTarget> mappings = projectTargetMapper.selectList(
                new LambdaQueryWrapper<ProjectDeployTarget>()
                        .eq(ProjectDeployTarget::getProjectId, projectId)
                        .orderByAsc(ProjectDeployTarget::getId));
        if (mappings.isEmpty()) {
            return List.of();
        }
        Map<Long, DeployTarget> targetsById = targetMapper.selectBatchIds(
                        mappings.stream().map(ProjectDeployTarget::getTargetId).toList())
                .stream().collect(Collectors.toMap(DeployTarget::getId, Function.identity()));
        return mappings.stream()
                .map(m -> new ProjectTargetResp(m, targetsById.get(m.getTargetId())))
                .toList();
    }

    /** 项目可选目标集合（发布门禁第 4 项用） */
    public boolean isProjectTarget(Long projectId, Long targetId) {
        return projectTargetMapper.selectCount(new LambdaQueryWrapper<ProjectDeployTarget>()
                .eq(ProjectDeployTarget::getProjectId, projectId)
                .eq(ProjectDeployTarget::getTargetId, targetId)) > 0;
    }

    public DeployTarget getOrThrow(Long id) {
        DeployTarget target = targetMapper.selectById(id);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部署目标不存在: " + id);
        }
        return target;
    }

    private ProjectDeployTarget getMappingOrThrow(Long projectId, Long targetId) {
        ProjectDeployTarget mapping = projectTargetMapper.selectOne(new LambdaQueryWrapper<ProjectDeployTarget>()
                .eq(ProjectDeployTarget::getProjectId, projectId)
                .eq(ProjectDeployTarget::getTargetId, targetId));
        if (mapping == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "项目未挂接该部署目标: projectId=" + projectId + ", targetId=" + targetId);
        }
        return mapping;
    }

    private static void validateLevels(List<String> levels) {
        if (levels == null) {
            return;
        }
        for (String level : levels) {
            try {
                AgentLevelValue.valueOf(level);
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "非法 Agent 等级: " + level + "（可选 P0/P1/P2/P3）");
            }
        }
    }
}
