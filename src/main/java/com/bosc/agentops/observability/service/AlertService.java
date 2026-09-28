package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.observability.dto.AlertHandleReq;
import com.bosc.agentops.observability.dto.AlertListReq;
import com.bosc.agentops.observability.dto.AlertWebhookReq;
import com.bosc.agentops.observability.dto.ObsIdReq;
import com.bosc.agentops.observability.entity.AlertEvent;
import com.bosc.agentops.observability.entity.AlertStatus;
import com.bosc.agentops.observability.mapper.AlertEventMapper;
import com.bosc.agentops.observability.support.MaskingUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警：webhook 落库（FIRING，detail 脱敏）；list/detail 按项目隔离
 * （项目内 Agent 的告警 + agentId 为空的平台级告警）；handle 登记处置说明置 HANDLED。
 */
@Service
public class AlertService {

    public static final String MODULE = "observability";

    private final AlertEventMapper alertEventMapper;
    private final AgentService agentService;
    private final AuditService auditService;
    private final MaskingUtil maskingUtil;

    public AlertService(AlertEventMapper alertEventMapper, AgentService agentService,
                        AuditService auditService, MaskingUtil maskingUtil) {
        this.alertEventMapper = alertEventMapper;
        this.agentService = agentService;
        this.auditService = auditService;
        this.maskingUtil = maskingUtil;
    }

    /** 机器通道入口：告警推送落库（detail 落库前脱敏） */
    @Transactional
    public AlertEvent ingest(AlertWebhookReq req) {
        AlertEvent event = new AlertEvent();
        event.setSource(req.getSource());
        event.setAlertKey(req.getAlertKey());
        event.setAgentId(req.getAgentId());
        event.setLevel(req.getLevel());
        event.setTitle(req.getTitle());
        event.setDetail(req.getDetail() == null ? null : maskingUtil.maskJson(req.getDetail().toString()));
        event.setStatus(AlertStatus.FIRING);
        alertEventMapper.insert(event);
        return event;
    }

    public List<AlertEvent> list(AlertListReq req) {
        List<Long> agentIds = agentIdsOfProject(req.getProjectId());
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .and(wrapper -> {
                    if (req.getAgentId() != null) {
                        wrapper.eq(AlertEvent::getAgentId, req.getAgentId());
                    } else {
                        wrapper.in(!agentIds.isEmpty(), AlertEvent::getAgentId, agentIds)
                                .or().isNull(AlertEvent::getAgentId);
                    }
                })
                .eq(req.getStatus() != null, AlertEvent::getStatus, req.getStatus())
                .eq(req.getLevel() != null && !req.getLevel().isBlank(), AlertEvent::getLevel, req.getLevel())
                .orderByDesc(AlertEvent::getId));
    }

    public AlertEvent detail(ObsIdReq req) {
        AlertEvent event = getOrThrow(req.getId());
        requireProjectScope(event, req.getProjectId());
        return event;
    }

    @Transactional
    public AlertEvent handle(AlertHandleReq req) {
        AlertEvent event = getOrThrow(req.getId());
        requireProjectScope(event, req.getProjectId());
        if (event.getStatus() != AlertStatus.FIRING) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "告警非 FIRING，无法登记处置: id=" + event.getId() + ", status=" + event.getStatus());
        }
        event.setStatus(AlertStatus.HANDLED);
        event.setHandleNote(req.getNote());
        event.setHandledBy(RequestContext.currentUserId());
        event.setHandledAt(LocalDateTime.now());
        alertEventMapper.updateById(event);
        auditService.record(MODULE, "alert.handle", "alert_event", event.getId(),
                Map.of("alertKey", event.getAlertKey() == null ? "" : event.getAlertKey(),
                        "note", req.getNote()));
        return event;
    }

    public AlertEvent getOrThrow(Long id) {
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "告警事件不存在: " + id);
        }
        return event;
    }

    /** 告警挂在 Agent 上时，操作必须落在 Agent 所属项目（防跨项目处置） */
    private void requireProjectScope(AlertEvent event, Long projectId) {
        if (event.getAgentId() == null) {
            return;
        }
        Agent agent = agentService.getOrThrow(event.getAgentId());
        agentService.requireOwnerProject(agent, projectId);
    }

    private List<Long> agentIdsOfProject(Long projectId) {
        AgentListReq listReq = new AgentListReq();
        listReq.setProjectId(projectId);
        return agentService.list(listReq).stream().map(Agent::getId).toList();
    }
}
