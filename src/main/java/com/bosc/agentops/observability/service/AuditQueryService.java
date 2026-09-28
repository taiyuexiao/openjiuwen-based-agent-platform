package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.audit.entity.AuditEvent;
import com.bosc.agentops.common.audit.mapper.AuditEventMapper;
import com.bosc.agentops.observability.dto.AuditQueryReq;
import com.bosc.agentops.observability.support.MaskingUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计查询：直接复用 foundation 的 audit_event 表（不另建）。
 * 权限点 obs:read 由切面按 projectId 校验（审计事件为平台全局表，查询面仅做权限门禁 + detail 兜底脱敏）。
 */
@Service
public class AuditQueryService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    private final AuditEventMapper auditEventMapper;
    private final MaskingUtil maskingUtil;

    public AuditQueryService(AuditEventMapper auditEventMapper, MaskingUtil maskingUtil) {
        this.auditEventMapper = auditEventMapper;
        this.maskingUtil = maskingUtil;
    }

    public List<AuditEvent> query(AuditQueryReq req) {
        int limit = req.getLimit() == null || req.getLimit() <= 0
                ? DEFAULT_LIMIT : Math.min(req.getLimit(), MAX_LIMIT);
        List<AuditEvent> events = auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(req.getModule() != null && !req.getModule().isBlank(),
                        AuditEvent::getModule, req.getModule())
                .eq(req.getUserId() != null && !req.getUserId().isBlank(),
                        AuditEvent::getUserId, req.getUserId())
                .eq(req.getResourceType() != null && !req.getResourceType().isBlank(),
                        AuditEvent::getResourceType, req.getResourceType())
                .eq(req.getResourceId() != null && !req.getResourceId().isBlank(),
                        AuditEvent::getResourceId, req.getResourceId())
                .ge(req.getCreatedFrom() != null, AuditEvent::getCreatedAt, req.getCreatedFrom())
                .le(req.getCreatedTo() != null, AuditEvent::getCreatedAt, req.getCreatedTo())
                .orderByDesc(AuditEvent::getId)
                .last("LIMIT " + limit));
        events.forEach(event -> event.setDetail(maskingUtil.maskJson(event.getDetail())));
        return events;
    }
}
