package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.KnowledgeBaseCreateReq;
import com.bosc.agentops.modelknowledge.dto.KnowledgeBaseUpdateReq;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBase;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 知识库平台级目录管理。
 * 平台级目录操作本期约定为「认证用户 + 记录操作人」（与 project:create 同级），后续接平台运营角色收紧。
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeBase create(KnowledgeBaseCreateReq req) {
        String userId = RequestContext.currentUserId();
        KnowledgeBase kb = new KnowledgeBase();
        kb.setCode(req.getCode());
        kb.setName(req.getName());
        kb.setKbType(req.getKbType());
        kb.setEndpoint(req.getEndpoint());
        kb.setConnectionConfig(JsonSupport.toJson(objectMapper, req.getConnectionConfig()));
        kb.setOwnerId(userId);
        kb.setStatus(CatalogStatus.ENABLED);
        kb.setCreatedBy(userId);
        try {
            knowledgeBaseMapper.insert(kb);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "知识库 code 已存在: " + req.getCode());
        }
        auditService.record(ModelCatalogService.MODULE, "kb.create", "knowledge_base", kb.getId(),
                Map.of("code", kb.getCode(), "name", kb.getName()));
        return kb;
    }

    @Transactional
    public KnowledgeBase update(KnowledgeBaseUpdateReq req) {
        KnowledgeBase kb = getOrThrow(req.getId());
        kb.setName(req.getName());
        kb.setEndpoint(req.getEndpoint());
        if (req.getConnectionConfig() != null) {
            kb.setConnectionConfig(JsonSupport.toJson(objectMapper, req.getConnectionConfig()));
        }
        knowledgeBaseMapper.updateById(kb);
        auditService.record(ModelCatalogService.MODULE, "kb.update", "knowledge_base", kb.getId(),
                Map.of("code", kb.getCode(), "name", kb.getName()));
        return kb;
    }

    @Transactional
    public KnowledgeBase disable(Long id) {
        KnowledgeBase kb = getOrThrow(id);
        if (kb.getStatus() == CatalogStatus.DISABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "知识库已是停用状态: " + id);
        }
        kb.setStatus(CatalogStatus.DISABLED);
        knowledgeBaseMapper.updateById(kb);
        // disable 语义：停用后 listRefs/checkKbAllowed 不再放行，历史授权与引用记录保留
        auditService.record(ModelCatalogService.MODULE, "kb.disable", "knowledge_base", kb.getId(),
                Map.of("code", kb.getCode()));
        return kb;
    }

    public List<KnowledgeBase> list() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .orderByAsc(KnowledgeBase::getId));
    }

    public KnowledgeBase getOrThrow(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: " + id);
        }
        return kb;
    }
}
