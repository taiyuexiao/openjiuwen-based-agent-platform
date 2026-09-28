package com.bosc.agentops.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.delivery.dto.AgentLevelAgentReq;
import com.bosc.agentops.delivery.dto.AgentLevelConfirmReq;
import com.bosc.agentops.delivery.entity.AgentLevel;
import com.bosc.agentops.delivery.mapper.AgentLevelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 分级结论：外部评审形成，平台记录、校验并应用（部署目标可选范围、运行保障参数映射）。
 * confirm 新等级时旧生效记录自动失效；disable 使当前生效结论失效（发布门禁将判定「等级无效」）。
 */
@Service
public class AgentLevelService {

    private final AgentLevelMapper levelMapper;
    private final AgentService agentService;
    private final AuditService auditService;

    public AgentLevelService(AgentLevelMapper levelMapper, AgentService agentService, AuditService auditService) {
        this.levelMapper = levelMapper;
        this.agentService = agentService;
        this.auditService = auditService;
    }

    @Transactional
    public AgentLevel confirm(AgentLevelConfirmReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        levelMapper.update(null, new LambdaUpdateWrapper<AgentLevel>()
                .eq(AgentLevel::getAgentId, agent.getId())
                .eq(AgentLevel::getEffective, true)
                .set(AgentLevel::getEffective, false));
        AgentLevel level = new AgentLevel();
        level.setAgentId(agent.getId());
        level.setLevel(req.getLevel());
        level.setSource(req.getSource());
        level.setEffective(true);
        level.setConfirmedBy(userId);
        level.setConfirmedAt(LocalDateTime.now());
        level.setCreatedBy(userId);
        levelMapper.insert(level);
        auditService.record(ArtifactService.MODULE, "level.confirm", "delivery_agent_level", level.getId(),
                Map.of("agentId", agent.getId(), "level", level.getLevel().name()));
        return level;
    }

    @Transactional
    public void disable(AgentLevelAgentReq req) {
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        AgentLevel effective = getEffective(agent.getId());
        if (effective == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 无生效中的分级结论: agentId=" + agent.getId());
        }
        effective.setEffective(false);
        levelMapper.updateById(effective);
        auditService.record(ArtifactService.MODULE, "level.disable", "delivery_agent_level", effective.getId(),
                Map.of("agentId", agent.getId(), "level", effective.getLevel().name()));
    }

    public List<AgentLevel> list(AgentLevelAgentReq req) {
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        return levelMapper.selectList(new LambdaQueryWrapper<AgentLevel>()
                .eq(AgentLevel::getAgentId, req.getAgentId())
                .orderByAsc(AgentLevel::getId));
    }

    /** 当前生效的分级结论，无则 null */
    public AgentLevel getEffective(Long agentId) {
        return levelMapper.selectOne(new LambdaQueryWrapper<AgentLevel>()
                .eq(AgentLevel::getAgentId, agentId)
                .eq(AgentLevel::getEffective, true)
                .orderByDesc(AgentLevel::getId)
                .last("LIMIT 1"));
    }
}
