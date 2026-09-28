package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.KbGrantReq;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBase;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseGrant;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseGrantMapper;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 项目级知识库授权（grant/revoke/list）。grant 幂等：重复授权视为更新授权范围。
 */
@Service
public class KnowledgeBaseGrantService {

    private final KnowledgeBaseGrantMapper knowledgeBaseGrantMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ProjectService projectService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseGrantService(KnowledgeBaseGrantMapper knowledgeBaseGrantMapper,
                                     KnowledgeBaseService knowledgeBaseService,
                                     ProjectService projectService,
                                     AuditService auditService,
                                     ObjectMapper objectMapper) {
        this.knowledgeBaseGrantMapper = knowledgeBaseGrantMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.projectService = projectService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeBaseGrant grant(Long projectId, KbGrantReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        KnowledgeBase kb = knowledgeBaseService.getOrThrow(req.getKbId());
        if (kb.getStatus() != CatalogStatus.ENABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "知识库已停用，不能授权: " + req.getKbId());
        }
        String scope = JsonSupport.toJson(objectMapper, req.getScope());

        KnowledgeBaseGrant grant = knowledgeBaseGrantMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseGrant>()
                        .eq(KnowledgeBaseGrant::getKbId, req.getKbId())
                        .eq(KnowledgeBaseGrant::getProjectId, projectId));
        if (grant == null) {
            grant = new KnowledgeBaseGrant();
            grant.setKbId(req.getKbId());
            grant.setProjectId(projectId);
            grant.setGrantScope(scope);
            grant.setGrantedBy(userId);
            knowledgeBaseGrantMapper.insert(grant);
        } else {
            grant.setGrantScope(scope);
            grant.setGrantedBy(userId);
            knowledgeBaseGrantMapper.updateById(grant);
        }
        auditService.record(ModelCatalogService.MODULE, "kb.grant", "knowledge_base_grant", grant.getId(),
                Map.of("projectId", projectId, "kbId", req.getKbId()));
        return grant;
    }

    @Transactional
    public void revoke(Long projectId, Long kbId) {
        projectService.getOrThrow(projectId);
        KnowledgeBaseGrant grant = knowledgeBaseGrantMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBaseGrant>()
                        .eq(KnowledgeBaseGrant::getKbId, kbId)
                        .eq(KnowledgeBaseGrant::getProjectId, projectId));
        if (grant == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "授权不存在: projectId=" + projectId + ", kbId=" + kbId);
        }
        knowledgeBaseGrantMapper.deleteById(grant.getId());
        auditService.record(ModelCatalogService.MODULE, "kb.revoke", "knowledge_base_grant", grant.getId(),
                Map.of("projectId", projectId, "kbId", kbId));
    }

    public List<KnowledgeBaseGrant> list(Long projectId) {
        projectService.getOrThrow(projectId);
        return knowledgeBaseGrantMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseGrant>()
                .eq(KnowledgeBaseGrant::getProjectId, projectId)
                .orderByAsc(KnowledgeBaseGrant::getId));
    }
}
