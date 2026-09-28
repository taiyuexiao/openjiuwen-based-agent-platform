package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.KbRefBindReq;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBase;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseGrant;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseGrantMapper;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseRefMapper;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库引用绑定（bind/unbind/list）：保留知识库-项目-Agent 关联关系。
 * agentId 为弱引用（Agent 实体由模块 02 提供），本期不做存在性校验。
 */
@Service
public class KnowledgeBaseRefService {

    private final KnowledgeBaseRefMapper knowledgeBaseRefMapper;
    private final KnowledgeBaseGrantMapper knowledgeBaseGrantMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ProjectService projectService;
    private final AuditService auditService;

    public KnowledgeBaseRefService(KnowledgeBaseRefMapper knowledgeBaseRefMapper,
                                   KnowledgeBaseGrantMapper knowledgeBaseGrantMapper,
                                   KnowledgeBaseService knowledgeBaseService,
                                   ProjectService projectService,
                                   AuditService auditService) {
        this.knowledgeBaseRefMapper = knowledgeBaseRefMapper;
        this.knowledgeBaseGrantMapper = knowledgeBaseGrantMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public KnowledgeBaseRef bind(Long projectId, KbRefBindReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(projectId);
        projectService.requireActive(project);
        KnowledgeBase kb = knowledgeBaseService.getOrThrow(req.getKbId());
        if (kb.getStatus() != CatalogStatus.ENABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "知识库已停用，不能绑定引用: " + req.getKbId());
        }
        // 绑定前必须先有项目级授权：ref 依附于 grant 的授权范围
        Long grantCount = knowledgeBaseGrantMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseGrant>()
                        .eq(KnowledgeBaseGrant::getKbId, req.getKbId())
                        .eq(KnowledgeBaseGrant::getProjectId, projectId));
        if (grantCount == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "项目未获得该知识库授权，不能绑定引用: kbId=" + req.getKbId());
        }
        LambdaQueryWrapper<KnowledgeBaseRef> dupWrapper = new LambdaQueryWrapper<KnowledgeBaseRef>()
                .eq(KnowledgeBaseRef::getKbId, req.getKbId())
                .eq(KnowledgeBaseRef::getProjectId, projectId);
        if (req.getAgentId() == null) {
            dupWrapper.isNull(KnowledgeBaseRef::getAgentId);
        } else {
            dupWrapper.eq(KnowledgeBaseRef::getAgentId, req.getAgentId());
        }
        if (knowledgeBaseRefMapper.selectCount(dupWrapper) > 0) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "引用已存在: kbId=" + req.getKbId() + ", agentId=" + req.getAgentId());
        }

        KnowledgeBaseRef ref = new KnowledgeBaseRef();
        ref.setKbId(req.getKbId());
        ref.setProjectId(projectId);
        ref.setAgentId(req.getAgentId());
        ref.setRefVersion(req.getRefVersion());
        ref.setCreatedBy(userId);
        knowledgeBaseRefMapper.insert(ref);

        Map<String, Object> detail = new HashMap<>();
        detail.put("projectId", projectId);
        detail.put("kbId", req.getKbId());
        detail.put("agentId", req.getAgentId());
        auditService.record(ModelCatalogService.MODULE, "kb.ref.bind", "knowledge_base_ref", ref.getId(), detail);
        return ref;
    }

    @Transactional
    public void unbind(Long projectId, Long refId) {
        projectService.getOrThrow(projectId);
        KnowledgeBaseRef ref = knowledgeBaseRefMapper.selectById(refId);
        if (ref == null || !ref.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "引用不存在: projectId=" + projectId + ", refId=" + refId);
        }
        knowledgeBaseRefMapper.deleteById(refId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("projectId", projectId);
        detail.put("kbId", ref.getKbId());
        detail.put("agentId", ref.getAgentId());
        auditService.record(ModelCatalogService.MODULE, "kb.ref.unbind", "knowledge_base_ref", refId, detail);
    }

    public List<KnowledgeBaseRef> list(Long projectId, Long agentId) {
        projectService.getOrThrow(projectId);
        return knowledgeBaseRefMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseRef>()
                .eq(KnowledgeBaseRef::getProjectId, projectId)
                .eq(agentId != null, KnowledgeBaseRef::getAgentId, agentId)
                .orderByAsc(KnowledgeBaseRef::getId));
    }
}
