package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.mapper.DeploymentMapper;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.entity.CallerType;
import com.bosc.agentops.governance.entity.InvocationRecord;
import com.bosc.agentops.governance.entity.InvocationStatus;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.mapper.InvocationRecordMapper;
import com.bosc.agentops.project.entity.EnvType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 统一调用入口（运行时面）。流程：
 * 解析调用方（X-Caller-Id + X-Caller-Type，缺省拒绝 401）→ CallerPolicy 存在且 ACTIVE（否则 403，
 * sharedToken 非空时校验 Bearer 共享密钥）→ 内存滑动窗口限流（429）→ 路由解析（ACTIVE ServiceRoute，
 * X-Agent-Version 可选指定版本，无匹配 404）→ 转发到 Deployment.instanceUrl（SSE 流式透传，
 * 超时按 CallerPolicy.timeoutMs）→ 落 InvocationRecord（traceId/状态/延迟/错误摘要）。
 * 上游不可达 → 502。除身份缺失外，各阶段拒绝/失败均落 InvocationRecord。
 */
@Service
public class InvokeGatewayService {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(InvokeGatewayService.class);

    private final AgentMapper agentMapper;
    private final CallerPolicyService callerPolicyService;
    private final RouteService routeService;
    private final DeploymentMapper deploymentMapper;
    private final RateLimitService rateLimitService;
    private final UpstreamForwarder upstreamForwarder;
    private final InvocationRecordMapper invocationRecordMapper;

    public InvokeGatewayService(AgentMapper agentMapper, CallerPolicyService callerPolicyService,
                                RouteService routeService, DeploymentMapper deploymentMapper,
                                RateLimitService rateLimitService, UpstreamForwarder upstreamForwarder,
                                InvocationRecordMapper invocationRecordMapper) {
        this.agentMapper = agentMapper;
        this.callerPolicyService = callerPolicyService;
        this.routeService = routeService;
        this.deploymentMapper = deploymentMapper;
        this.rateLimitService = rateLimitService;
        this.upstreamForwarder = upstreamForwarder;
        this.invocationRecordMapper = invocationRecordMapper;
    }

    public void invoke(String agentCode, String envPath, String callerId, String callerTypeValue,
                       String versionHeader, String authorization, String contentType, byte[] body,
                       HttpServletResponse response) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        response.setHeader(TRACE_ID_HEADER, traceId);

        // 1. 调用方身份（机器身份：X-Caller-Id + X-Caller-Type，缺省拒绝）
        CallerType callerType = parseCallerType(callerTypeValue);
        if (callerId == null || callerId.isBlank() || callerType == null) {
            throw new GovRuntimeException(401, ErrorCode.UNAUTHORIZED,
                    "缺少调用方身份头（X-Caller-Id / X-Caller-Type: USER/HIAGENT/AGENT）");
        }
        EnvType env = parseEnv(envPath);

        // 2. Agent 解析（code 项目内唯一，跨项目可重名：遍历候选取首个有 ACTIVE 策略的）
        List<Agent> agents = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCode, agentCode).orderByAsc(Agent::getId));
        if (agents.isEmpty()) {
            record(traceId, callerType, callerId, null, env.name(), null, null,
                    InvocationStatus.REJECTED, start, "Agent 不存在: " + agentCode);
            throw new GovRuntimeException(404, ErrorCode.NOT_FOUND, "Agent 不存在: " + agentCode);
        }

        // 3. CallerPolicy 校验（身份）
        Agent agent = null;
        CallerPolicy policy = null;
        for (Agent candidate : agents) {
            CallerPolicy p = callerPolicyService.findActive(callerType, callerId, candidate.getId(), env.name());
            if (p != null) {
                agent = candidate;
                policy = p;
                break;
            }
        }
        if (policy == null) {
            record(traceId, callerType, callerId, agents.get(0).getId(), env.name(), null, null,
                    InvocationStatus.REJECTED, start, "无生效的调用方策略（CallerPolicy）");
            throw new GovRuntimeException(403, ErrorCode.FORBIDDEN,
                    "调用方未获授权: callerType=" + callerType + ", callerId=" + callerId
                            + ", agentCode=" + agentCode + ", env=" + env);
        }

        // 3b. 共享密钥校验（策略配置了 sharedToken 时强制）
        if (policy.getSharedToken() != null && !policy.getSharedToken().isBlank()) {
            String expected = "Bearer " + policy.getSharedToken();
            if (!expected.equals(authorization)) {
                record(traceId, callerType, callerId, agent.getId(), env.name(), null, null,
                        InvocationStatus.REJECTED, start, "共享密钥缺失或不匹配");
                throw new GovRuntimeException(401, ErrorCode.UNAUTHORIZED, "共享密钥缺失或不匹配");
            }
        }

        // 4. 限流（内存滑动窗口）
        if (!rateLimitService.tryAcquire(policy.getId(), policy.getRateLimitPerMin())) {
            record(traceId, callerType, callerId, agent.getId(), env.name(), null, null,
                    InvocationStatus.REJECTED, start,
                    "限流: 超过 rateLimitPerMin=" + policy.getRateLimitPerMin());
            throw new GovRuntimeException(429, ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.getDefaultMessage()
                            + ": rateLimitPerMin=" + policy.getRateLimitPerMin());
        }

        // 5. 路由解析（ACTIVE；X-Agent-Version 可选指定版本）
        ServiceRoute route = routeService.findActiveRoute(agent.getId(), env.name(), versionHeader);
        if (route == null) {
            record(traceId, callerType, callerId, agent.getId(), env.name(), null, null,
                    InvocationStatus.REJECTED, start, "无 ACTIVE 路由"
                            + (versionHeader == null ? "" : ": version=" + versionHeader));
            throw new GovRuntimeException(404, ErrorCode.NOT_FOUND,
                    "无可用路由: agentCode=" + agentCode + ", env=" + env
                            + (versionHeader == null ? "" : ", version=" + versionHeader));
        }
        Deployment deployment = deploymentMapper.selectById(route.getDeploymentId());
        if (deployment == null || deployment.getInstanceUrl() == null || deployment.getInstanceUrl().isBlank()) {
            record(traceId, callerType, callerId, agent.getId(), env.name(), route.getId(),
                    route.getAgentVersion(), InvocationStatus.FAILED, start, "路由目标部署实例无访问地址");
            throw new GovRuntimeException(502, ErrorCode.UPSTREAM_ERROR, "路由目标部署实例无访问地址");
        }

        // 6. 转发（SSE 透传由 UpstreamForwarder 流式拷贝保证）；超时按 CallerPolicy.timeoutMs
        UpstreamForwarder.ForwardResult result;
        try {
            result = upstreamForwarder.forward(
                    deployment.getInstanceUrl(), body, contentType, policy.getTimeoutMs(), response);
        } catch (GovRuntimeException e) {
            // 上游不可达/超时（响应未提交）：落 FAILED 记录后按 502 抛出
            record(traceId, callerType, callerId, agent.getId(), env.name(), route.getId(),
                    route.getAgentVersion(), InvocationStatus.FAILED, start, e.getMessage());
            throw e;
        }
        if (!result.completed()) {
            record(traceId, callerType, callerId, agent.getId(), env.name(), route.getId(),
                    route.getAgentVersion(), InvocationStatus.FAILED, start, "上游连接中断（流式传输未完成）");
            return;
        }
        if (result.upstreamStatus() >= 500) {
            record(traceId, callerType, callerId, agent.getId(), env.name(), route.getId(),
                    route.getAgentVersion(), InvocationStatus.FAILED, start,
                    "上游返回 HTTP " + result.upstreamStatus());
            return;
        }
        record(traceId, callerType, callerId, agent.getId(), env.name(), route.getId(),
                route.getAgentVersion(), InvocationStatus.SUCCESS, start, null);
    }

    private void record(String traceId, CallerType callerType, String callerId, Long agentId, String env,
                        Long routeId, String agentVersion, InvocationStatus status, long start, String error) {
        try {
            InvocationRecord record = new InvocationRecord();
            record.setTraceId(traceId);
            record.setCallerType(callerType);
            record.setCallerId(callerId);
            record.setAgentId(agentId);
            record.setEnv(env);
            record.setRouteId(routeId);
            record.setAgentVersion(agentVersion);
            record.setStatus(status);
            record.setLatencyMs(System.currentTimeMillis() - start);
            record.setError(error == null ? null
                    : error.length() > 1000 ? error.substring(0, 1000) : error);
            invocationRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("InvocationRecord 落库失败: traceId={}", traceId, e);
        }
    }

    private static CallerType parseCallerType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CallerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static EnvType parseEnv(String env) {
        try {
            return EnvType.valueOf(env.trim().toUpperCase());
        } catch (Exception e) {
            throw new GovRuntimeException(400, ErrorCode.PARAM_INVALID, "非法环境标识: " + env);
        }
    }
}
