package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.governance.dto.CallerPolicyGrantReq;
import com.bosc.agentops.governance.dto.CallerPolicyListReq;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.entity.CallerType;
import com.bosc.agentops.governance.entity.PolicyStatus;
import com.bosc.agentops.governance.mapper.CallerPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 调用方策略：grant/revoke/list。grant 对同一 (callerType, callerId, agentId, env) 幂等：
 * 已存在 ACTIVE 策略 → 40901；已 REVOKED → 重新激活并更新流控参数（重建而非新增）。
 */
@Service
public class CallerPolicyService {

    static final int DEFAULT_RATE_LIMIT_PER_MIN = 600;
    static final int DEFAULT_TIMEOUT_MS = 30000;

    private final CallerPolicyMapper callerPolicyMapper;
    private final AgentService agentService;
    private final AuditService auditService;

    public CallerPolicyService(CallerPolicyMapper callerPolicyMapper, AgentService agentService,
                               AuditService auditService) {
        this.callerPolicyMapper = callerPolicyMapper;
        this.agentService = agentService;
        this.auditService = auditService;
    }

    @Transactional
    public CallerPolicy grant(CallerPolicyGrantReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        String env = req.getEnv().name();

        CallerPolicy existing = findAny(req.getCallerType(), req.getCallerId(), agent.getId(), env);
        if (existing != null) {
            if (existing.getStatus() == PolicyStatus.ACTIVE) {
                throw new BizException(ErrorCode.DUPLICATE,
                        "调用方策略已存在: callerType=" + req.getCallerType() + ", callerId="
                                + req.getCallerId() + ", agentId=" + agent.getId() + ", env=" + env);
            }
            existing.setStatus(PolicyStatus.ACTIVE);
            existing.setSharedToken(req.getSharedToken());
            existing.setRateLimitPerMin(req.getRateLimitPerMin() == null
                    ? DEFAULT_RATE_LIMIT_PER_MIN : req.getRateLimitPerMin());
            existing.setTimeoutMs(req.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : req.getTimeoutMs());
            callerPolicyMapper.updateById(existing);
            auditService.record(RouteService.MODULE, "caller-policy.grant", "gov_caller_policy",
                    existing.getId(), Map.of("callerType", req.getCallerType().name(),
                            "callerId", req.getCallerId(), "agentId", agent.getId(), "env", env,
                            "reactivated", true));
            return existing;
        }

        CallerPolicy policy = new CallerPolicy();
        policy.setCallerType(req.getCallerType());
        policy.setCallerId(req.getCallerId());
        policy.setAgentId(agent.getId());
        policy.setEnv(env);
        policy.setSharedToken(req.getSharedToken());
        policy.setRateLimitPerMin(req.getRateLimitPerMin() == null
                ? DEFAULT_RATE_LIMIT_PER_MIN : req.getRateLimitPerMin());
        policy.setTimeoutMs(req.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : req.getTimeoutMs());
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setCreatedBy(userId);
        callerPolicyMapper.insert(policy);
        auditService.record(RouteService.MODULE, "caller-policy.grant", "gov_caller_policy",
                policy.getId(), Map.of("callerType", req.getCallerType().name(),
                        "callerId", req.getCallerId(), "agentId", agent.getId(), "env", env));
        return policy;
    }

    @Transactional
    public CallerPolicy revoke(GovIdReq req) {
        CallerPolicy policy = getOrThrow(req.getId());
        Agent agent = agentService.getOrThrow(policy.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "调用方策略非 ACTIVE，无法撤销: id=" + policy.getId() + ", status=" + policy.getStatus());
        }
        policy.setStatus(PolicyStatus.REVOKED);
        callerPolicyMapper.updateById(policy);
        auditService.record(RouteService.MODULE, "caller-policy.revoke", "gov_caller_policy",
                policy.getId(), Map.of("callerType", policy.getCallerType().name(),
                        "callerId", policy.getCallerId(), "agentId", policy.getAgentId()));
        return policy;
    }

    public List<CallerPolicy> list(CallerPolicyListReq req) {
        List<Long> agentIds = agentIdsOfProject(req.getProjectId(), req.getAgentId());
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return callerPolicyMapper.selectList(new LambdaQueryWrapper<CallerPolicy>()
                .in(CallerPolicy::getAgentId, agentIds)
                .eq(req.getEnv() != null, CallerPolicy::getEnv,
                        req.getEnv() == null ? null : req.getEnv().name())
                .orderByAsc(CallerPolicy::getId));
    }

    public CallerPolicy getOrThrow(Long id) {
        CallerPolicy policy = callerPolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调用方策略不存在: " + id);
        }
        return policy;
    }

    /** ACTIVE 策略查询（运行时面鉴权用） */
    CallerPolicy findActive(CallerType callerType, String callerId, Long agentId, String env) {
        return callerPolicyMapper.selectOne(new LambdaQueryWrapper<CallerPolicy>()
                .eq(CallerPolicy::getCallerType, callerType)
                .eq(CallerPolicy::getCallerId, callerId)
                .eq(CallerPolicy::getAgentId, agentId)
                .eq(CallerPolicy::getEnv, env)
                .eq(CallerPolicy::getStatus, PolicyStatus.ACTIVE)
                .last("LIMIT 1"));
    }

    private CallerPolicy findAny(CallerType callerType, String callerId, Long agentId, String env) {
        return callerPolicyMapper.selectOne(new LambdaQueryWrapper<CallerPolicy>()
                .eq(CallerPolicy::getCallerType, callerType)
                .eq(CallerPolicy::getCallerId, callerId)
                .eq(CallerPolicy::getAgentId, agentId)
                .eq(CallerPolicy::getEnv, env)
                .last("LIMIT 1"));
    }

    private List<Long> agentIdsOfProject(Long projectId, Long agentId) {
        if (agentId != null) {
            Agent agent = agentService.getOrThrow(agentId);
            agentService.requireOwnerProject(agent, projectId);
            return List.of(agent.getId());
        }
        com.bosc.agentops.agentdev.dto.AgentListReq listReq = new com.bosc.agentops.agentdev.dto.AgentListReq();
        listReq.setProjectId(projectId);
        return agentService.list(listReq).stream().map(Agent::getId).toList();
    }
}
