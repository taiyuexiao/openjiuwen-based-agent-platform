package com.bosc.agentops.enterprise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.enterprise.dto.ItsmChangeListReq;
import com.bosc.agentops.enterprise.dto.ItsmChangeRecordReq;
import com.bosc.agentops.enterprise.entity.ItsmChangeRecord;
import com.bosc.agentops.enterprise.mapper.ItsmChangeRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ITSM 变更登记（本期刻意只做登记与查询，changeNo 全局唯一；
 * 真实审批流衔接属 06 投产流程，避免两个模块重复建状态机）。
 */
@Service
public class ItsmChangeService {

    static final String MODULE = "enterprise-integration";

    static final String STATUS_RECORDED = "RECORDED";

    private final ItsmChangeRecordMapper changeRecordMapper;
    private final AgentMapper agentMapper;
    private final AgentService agentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ItsmChangeService(ItsmChangeRecordMapper changeRecordMapper, AgentMapper agentMapper,
                             AgentService agentService, AuditService auditService, ObjectMapper objectMapper) {
        this.changeRecordMapper = changeRecordMapper;
        this.agentMapper = agentMapper;
        this.agentService = agentService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ItsmChangeRecord record(ItsmChangeRecordReq req) {
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        ItsmChangeRecord record = new ItsmChangeRecord();
        record.setChangeNo(req.getChangeNo());
        record.setAgentId(req.getAgentId());
        record.setChangeType(req.getChangeType());
        record.setPayload(req.getPayload() == null || req.getPayload().isNull()
                ? null : req.getPayload().toString());
        record.setStatus(STATUS_RECORDED);
        record.setCreatedBy(RequestContext.currentUserId());
        try {
            changeRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "ITSM 变更单号已存在: " + req.getChangeNo());
        }
        auditService.record(MODULE, "itsm.change.record", "itsm_change_record", record.getId(),
                Map.of("changeNo", record.getChangeNo(), "agentId", record.getAgentId(),
                        "changeType", record.getChangeType()));
        return record;
    }

    public List<ItsmChangeRecord> list(ItsmChangeListReq req) {
        List<Agent> agents = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getProjectId, req.getProjectId())
                .eq(req.getAgentId() != null, Agent::getId, req.getAgentId()));
        if (agents.isEmpty()) {
            return List.of();
        }
        List<Long> agentIds = agents.stream().map(Agent::getId).toList();
        return changeRecordMapper.selectList(new LambdaQueryWrapper<ItsmChangeRecord>()
                .in(ItsmChangeRecord::getAgentId, agentIds)
                .orderByAsc(ItsmChangeRecord::getId));
    }
}
