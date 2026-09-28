package com.bosc.agentops.modelknowledge.service;

import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;

import java.util.List;

/**
 * 知识库访问 SPI，供其他模块（02 Agent 声明登记、底座 Retriever 插件配置拉取）依赖。
 */
public interface KnowledgeAccessService {

    /**
     * 判定项目/Agent 是否可访问知识库。
     * 规则：知识库 ENABLED 且项目级 grant 存在（grant_scope 为授权范围声明，本期仅要求存在，
     * 集合/标签过滤的运行时匹配待 02 模块查询上下文落地后实现）；
     * agentId 非空时优先按 ref 判定：该 agent 级 ref 或项目级 ref（agent_id 为空）存在才放行。
     */
    boolean checkKbAllowed(Long projectId, Long agentId, Long kbId);

    /** 项目下的知识库引用（agentId 非空时过滤到该 Agent）；已 disable 的知识库的引用不返回 */
    List<KnowledgeBaseRef> listRefs(Long projectId, Long agentId);
}
