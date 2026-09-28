package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBase;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseGrant;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseGrantMapper;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseMapper;
import com.bosc.agentops.modelknowledge.mapper.KnowledgeBaseRefMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * KnowledgeAccessService 实现。
 * disable 语义：知识库停用后 checkKbAllowed 拒绝、listRefs 不返回，历史授权/引用记录保留。
 */
@Service
public class KnowledgeAccessServiceImpl implements KnowledgeAccessService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseGrantMapper knowledgeBaseGrantMapper;
    private final KnowledgeBaseRefMapper knowledgeBaseRefMapper;

    public KnowledgeAccessServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                      KnowledgeBaseGrantMapper knowledgeBaseGrantMapper,
                                      KnowledgeBaseRefMapper knowledgeBaseRefMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseGrantMapper = knowledgeBaseGrantMapper;
        this.knowledgeBaseRefMapper = knowledgeBaseRefMapper;
    }

    @Override
    public boolean checkKbAllowed(Long projectId, Long agentId, Long kbId) {
        if (projectId == null || kbId == null) {
            return false;
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getStatus() != CatalogStatus.ENABLED) {
            return false;
        }
        // 项目级 grant 存在即视为 scope 匹配（grant_scope 的运行时过滤匹配待 02 模块落地后实现）
        Long grantCount = knowledgeBaseGrantMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseGrant>()
                        .eq(KnowledgeBaseGrant::getKbId, kbId)
                        .eq(KnowledgeBaseGrant::getProjectId, projectId));
        if (grantCount == 0) {
            return false;
        }
        if (agentId == null) {
            return true;
        }
        // agent 级查询优先按 ref 判定：agent 级 ref 或项目级 ref（agent_id 为空）存在才放行
        Long refCount = knowledgeBaseRefMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBaseRef>()
                        .eq(KnowledgeBaseRef::getKbId, kbId)
                        .eq(KnowledgeBaseRef::getProjectId, projectId)
                        .and(w -> w.eq(KnowledgeBaseRef::getAgentId, agentId)
                                .or().isNull(KnowledgeBaseRef::getAgentId)));
        return refCount > 0;
    }

    @Override
    public List<KnowledgeBaseRef> listRefs(Long projectId, Long agentId) {
        List<KnowledgeBaseRef> refs = knowledgeBaseRefMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBaseRef>()
                        .eq(KnowledgeBaseRef::getProjectId, projectId)
                        .eq(agentId != null, KnowledgeBaseRef::getAgentId, agentId)
                        .orderByAsc(KnowledgeBaseRef::getId));
        if (refs.isEmpty()) {
            return refs;
        }
        Set<Long> kbIds = refs.stream().map(KnowledgeBaseRef::getKbId).collect(Collectors.toSet());
        Map<Long, KnowledgeBase> kbById = knowledgeBaseMapper.selectBatchIds(kbIds).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));
        // 已 disable（或已删除）的知识库的引用不下发，记录本身保留
        return refs.stream()
                .filter(ref -> {
                    KnowledgeBase kb = kbById.get(ref.getKbId());
                    return kb != null && kb.getStatus() == CatalogStatus.ENABLED;
                })
                .toList();
    }
}
