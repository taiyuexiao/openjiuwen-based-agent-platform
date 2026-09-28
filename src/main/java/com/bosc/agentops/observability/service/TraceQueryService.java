package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.observability.dto.TraceDetailReq;
import com.bosc.agentops.observability.dto.TraceQueryReq;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.mapper.TraceSpanMapper;
import com.bosc.agentops.observability.support.MaskingUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 链路查询面：全部按项目权限过滤（span 归属限定在 projectId 的 Agent 集合内），返回前 attrs 兜底脱敏。
 * 权限点 obs:read 由切面校验（四角色的 obs:read 与 project:read 授予集一致）。
 */
@Service
public class TraceQueryService {

    static final int DEFAULT_LIMIT = 200;
    static final int MAX_LIMIT = 1000;

    private final TraceSpanMapper traceSpanMapper;
    private final AgentService agentService;
    private final MaskingUtil maskingUtil;

    public TraceQueryService(TraceSpanMapper traceSpanMapper, AgentService agentService,
                             MaskingUtil maskingUtil) {
        this.traceSpanMapper = traceSpanMapper;
        this.agentService = agentService;
        this.maskingUtil = maskingUtil;
    }

    public List<TraceSpan> query(TraceQueryReq req) {
        List<Long> agentIds = agentIdsOfProject(req.getProjectId());
        if (agentIds.isEmpty()) {
            return List.of();
        }
        if (req.getAgentId() != null && !agentIds.contains(req.getAgentId())) {
            // 指定了其他项目的 Agent：按项目隔离返回空
            return List.of();
        }
        int limit = limit(req.getLimit(), DEFAULT_LIMIT);
        List<TraceSpan> spans = traceSpanMapper.selectList(new LambdaQueryWrapper<TraceSpan>()
                .in(TraceSpan::getAgentId, agentIds)
                .eq(req.getAgentId() != null, TraceSpan::getAgentId, req.getAgentId())
                .eq(req.getTraceId() != null && !req.getTraceId().isBlank(),
                        TraceSpan::getTraceId, req.getTraceId())
                .eq(req.getEnv() != null && !req.getEnv().isBlank(), TraceSpan::getEnv, req.getEnv())
                .ge(req.getCreatedFrom() != null, TraceSpan::getCreatedAt, req.getCreatedFrom())
                .le(req.getCreatedTo() != null, TraceSpan::getCreatedAt, req.getCreatedTo())
                .orderByDesc(TraceSpan::getId)
                .last("LIMIT " + limit));
        spans.forEach(this::maskAttrs);
        return spans;
    }

    /** 链路还原：traceId 下全部 span 按开始时间排序；存在但不属于本项目 → 403 */
    public List<TraceSpan> detail(String traceId, TraceDetailReq req) {
        List<TraceSpan> spans = traceSpanMapper.selectList(new LambdaQueryWrapper<TraceSpan>()
                .eq(TraceSpan::getTraceId, traceId)
                .orderByAsc(TraceSpan::getStartTime)
                .orderByAsc(TraceSpan::getId));
        if (spans.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "链路不存在: " + traceId);
        }
        Set<Long> agentIds = Set.copyOf(agentIdsOfProject(req.getProjectId()));
        List<TraceSpan> scoped = spans.stream()
                .filter(span -> agentIds.contains(span.getAgentId()))
                .collect(Collectors.toList());
        if (scoped.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "链路不属于当前项目: " + traceId);
        }
        scoped.forEach(this::maskAttrs);
        return scoped;
    }

    private void maskAttrs(TraceSpan span) {
        span.setAttrs(maskingUtil.maskJson(span.getAttrs()));
    }

    private List<Long> agentIdsOfProject(Long projectId) {
        AgentListReq listReq = new AgentListReq();
        listReq.setProjectId(projectId);
        return agentService.list(listReq).stream().map(Agent::getId).toList();
    }

    private int limit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
