package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.common.permission.PermissionService;
import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.entity.InvocationStatus;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.observability.dto.DailyTrendItem;
import com.bosc.agentops.observability.dto.StatsOverviewReq;
import com.bosc.agentops.observability.dto.StatsOverviewResp;
import com.bosc.agentops.observability.dto.TopAgentItem;
import com.bosc.agentops.observability.entity.AlertEvent;
import com.bosc.agentops.observability.entity.AlertStatus;
import com.bosc.agentops.observability.entity.TraceSpan;
import com.bosc.agentops.observability.mapper.AlertEventMapper;
import com.bosc.agentops.observability.mapper.TraceSpanMapper;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 观测统计聚合：基于 gov_invocation_record / trace_span / alert_event。
 * 统计窗口为近 7 个自然日（含今天）；projectId 为空时做平台级聚合。
 * 项目级查询在 service 层手工校验调用方对该项目的 obs:read（接口本身为平台级注解，
 * 以便 projectId 为空时不触发 scope 解析失败）。
 */
@Service
public class ObservabilityStatsService {

    /** 统计窗口天数 */
    static final int WINDOW_DAYS = 7;
    static final int TOP_AGENTS_LIMIT = 5;

    private final InvocationRecordMapper invocationRecordMapper;
    private final TraceSpanMapper traceSpanMapper;
    private final AlertEventMapper alertEventMapper;
    private final AgentMapper agentMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;

    public ObservabilityStatsService(InvocationRecordMapper invocationRecordMapper,
                                     TraceSpanMapper traceSpanMapper,
                                     AlertEventMapper alertEventMapper,
                                     AgentMapper agentMapper,
                                     ProjectService projectService,
                                     PermissionService permissionService) {
        this.invocationRecordMapper = invocationRecordMapper;
        this.traceSpanMapper = traceSpanMapper;
        this.alertEventMapper = alertEventMapper;
        this.agentMapper = agentMapper;
        this.projectService = projectService;
        this.permissionService = permissionService;
    }

    public StatsOverviewResp overview(StatsOverviewReq req) {
        Long projectId = req == null ? null : req.getProjectId();
        List<Long> agentIds = null;
        if (projectId != null) {
            projectService.getOrThrow(projectId);
            String userId = RequestContext.currentUserId();
            if (!permissionService.check(userId, "obs:read", projectId)) {
                throw new BizException(ErrorCode.FORBIDDEN,
                        ErrorCode.FORBIDDEN.getDefaultMessage() + ": obs:read");
            }
            agentIds = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                            .eq(Agent::getProjectId, projectId))
                    .stream().map(Agent::getId).toList();
        }

        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(WINDOW_DAYS - 1L).atStartOfDay();
        List<InvocationRecord> records = queryRecords(agentIds, windowStart);

        StatsOverviewResp resp = new StatsOverviewResp();
        resp.setScope(projectId == null ? "PLATFORM" : "PROJECT");
        resp.setProjectId(projectId);
        resp.setTotalCalls(records.size());

        long success = records.stream().filter(r -> r.getStatus() == InvocationStatus.SUCCESS).count();
        resp.setSuccessRate(records.isEmpty() ? 0 : (double) success / records.size());
        resp.setAvgLatencyMs(records.stream().filter(r -> r.getLatencyMs() != null)
                .mapToLong(InvocationRecord::getLatencyMs).average().orElse(0));
        resp.setDailyTrend(buildDailyTrend(records, today));
        resp.setTopAgents(buildTopAgents(records));
        resp.setSpanKindDist(buildSpanKindDist(agentIds, windowStart));
        resp.setAlertOpenCount(countFiringAlerts(agentIds));
        return resp;
    }

    /** agentIds 为 null 表示平台级（不按 Agent 过滤）；空列表表示项目下无 Agent（必然零命中） */
    private List<InvocationRecord> queryRecords(List<Long> agentIds, LocalDateTime windowStart) {
        if (agentIds != null && agentIds.isEmpty()) {
            return List.of();
        }
        return invocationRecordMapper.selectList(new LambdaQueryWrapper<InvocationRecord>()
                .ge(InvocationRecord::getCreatedAt, windowStart)
                .in(agentIds != null, InvocationRecord::getAgentId, agentIds));
    }

    private List<DailyTrendItem> buildDailyTrend(List<InvocationRecord> records, LocalDate today) {
        Map<LocalDate, List<InvocationRecord>> byDate = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate()));
        List<DailyTrendItem> trend = new ArrayList<>();
        for (int i = WINDOW_DAYS - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<InvocationRecord> dayRecords = byDate.getOrDefault(date, List.of());
            long daySuccess = dayRecords.stream()
                    .filter(r -> r.getStatus() == InvocationStatus.SUCCESS).count();
            trend.add(new DailyTrendItem(date.toString(), dayRecords.size(),
                    daySuccess, dayRecords.size() - daySuccess));
        }
        return trend;
    }

    private List<TopAgentItem> buildTopAgents(List<InvocationRecord> records) {
        Map<Long, Long> callsByAgent = records.stream()
                .filter(r -> r.getAgentId() != null)
                .collect(Collectors.groupingBy(InvocationRecord::getAgentId, Collectors.counting()));
        return callsByAgent.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_AGENTS_LIMIT)
                .map(e -> new TopAgentItem(e.getKey(), e.getValue()))
                .toList();
    }

    private Map<String, Long> buildSpanKindDist(List<Long> agentIds, LocalDateTime windowStart) {
        if (agentIds != null && agentIds.isEmpty()) {
            return Map.of();
        }
        List<TraceSpan> spans = traceSpanMapper.selectList(new LambdaQueryWrapper<TraceSpan>()
                .ge(TraceSpan::getCreatedAt, windowStart)
                .in(agentIds != null, TraceSpan::getAgentId, agentIds));
        return spans.stream()
                .map(s -> s.getSpanKind() == null ? "OTHER" : s.getSpanKind().name())
                .collect(Collectors.groupingBy(kind -> kind, TreeMap::new, Collectors.counting()));
    }

    private long countFiringAlerts(List<Long> agentIds) {
        if (agentIds != null && agentIds.isEmpty()) {
            return 0;
        }
        return alertEventMapper.selectCount(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getStatus, AlertStatus.FIRING)
                .in(Objects.nonNull(agentIds), AlertEvent::getAgentId, agentIds));
    }
}
