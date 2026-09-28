package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.entity.DeploymentStatus;
import com.bosc.agentops.delivery.entity.Release;
import com.bosc.agentops.delivery.mapper.DeployTargetMapper;
import com.bosc.agentops.delivery.mapper.DeploymentMapper;
import com.bosc.agentops.delivery.mapper.ReleaseMapper;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.dto.RouteListReq;
import com.bosc.agentops.governance.dto.RouteSyncReq;
import com.bosc.agentops.governance.entity.RouteStatus;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.mapper.ServiceRouteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 服务路由：sync 从 delivery 指定 agent+env 的 RUNNING 部署生成/更新路由（发布成功 ≠ 立即开流量，
 * 路由 ACTIVE 由 sync 显式产生）。route_revision 在 (agentId, env) 内递增；新路由上位时旧 ACTIVE 路由
 * 置 DRAINED。幂等：同一 deployment 重复 sync 返回既有路由，不产生新 revision。
 */
@Service
public class RouteService {

    public static final String MODULE = "governance";

    private final ServiceRouteMapper routeMapper;
    private final ReleaseMapper releaseMapper;
    private final DeploymentMapper deploymentMapper;
    private final DeployTargetMapper deployTargetMapper;
    private final AgentService agentService;
    private final AuditService auditService;

    public RouteService(ServiceRouteMapper routeMapper, ReleaseMapper releaseMapper,
                        DeploymentMapper deploymentMapper, DeployTargetMapper deployTargetMapper,
                        AgentService agentService, AuditService auditService) {
        this.routeMapper = routeMapper;
        this.releaseMapper = releaseMapper;
        this.deploymentMapper = deploymentMapper;
        this.deployTargetMapper = deployTargetMapper;
        this.agentService = agentService;
        this.auditService = auditService;
    }

    /**
     * 从 RUNNING 部署同步路由。幂等：deployment_id 已有路由时直接返回。
     * 新部署上位：同 agent+env 旧 ACTIVE 路由置 DRAINED，revision 递增。
     */
    @Transactional
    public ServiceRoute sync(RouteSyncReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        String env = req.getEnv().name();

        Deployment running = findRunningDeployment(agent.getId(), env);
        if (running == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "该 Agent 在环境 " + env + " 无 RUNNING 部署: agentId=" + agent.getId());
        }
        Release release = releaseMapper.selectById(running.getReleaseId());
        if (release == null || !release.getAgentVersion().equals(req.getAgentVersion())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "请求版本与 RUNNING 部署版本不一致: 请求=" + req.getAgentVersion()
                            + ", 部署=" + (release == null ? null : release.getAgentVersion()));
        }

        ServiceRoute existing = routeMapper.selectOne(new LambdaQueryWrapper<ServiceRoute>()
                .eq(ServiceRoute::getDeploymentId, running.getId()));
        if (existing != null) {
            return existing;
        }

        List<ServiceRoute> actives = routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                .eq(ServiceRoute::getAgentId, agent.getId())
                .eq(ServiceRoute::getEnv, env)
                .eq(ServiceRoute::getStatus, RouteStatus.ACTIVE));
        for (ServiceRoute old : actives) {
            old.setStatus(RouteStatus.DRAINED);
            routeMapper.updateById(old);
        }

        int revision = nextRevision(agent.getId(), env);
        ServiceRoute route = new ServiceRoute();
        route.setAgentId(agent.getId());
        route.setEnv(env);
        route.setAgentVersion(req.getAgentVersion());
        route.setDeploymentId(running.getId());
        route.setRouteRevision(revision);
        route.setStatus(RouteStatus.ACTIVE);
        route.setCreatedBy(userId);
        routeMapper.insert(route);
        auditService.record(MODULE, "route.sync", "gov_service_route", route.getId(),
                Map.of("agentId", agent.getId(), "env", env, "agentVersion", req.getAgentVersion(),
                        "deploymentId", running.getId(), "routeRevision", revision,
                        "drainedRouteIds", actives.stream().map(ServiceRoute::getId).toList()));
        return route;
    }

    public List<ServiceRoute> list(RouteListReq req) {
        if (req.getAgentId() != null) {
            Agent agent = agentService.getOrThrow(req.getAgentId());
            agentService.requireOwnerProject(agent, req.getProjectId());
            return routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                    .eq(ServiceRoute::getAgentId, req.getAgentId())
                    .eq(req.getEnv() != null, ServiceRoute::getEnv,
                            req.getEnv() == null ? null : req.getEnv().name())
                    .orderByAsc(ServiceRoute::getId));
        }
        List<Long> agentIds = agentIdsOfProject(req.getProjectId());
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                .in(ServiceRoute::getAgentId, agentIds)
                .eq(req.getEnv() != null, ServiceRoute::getEnv,
                        req.getEnv() == null ? null : req.getEnv().name())
                .orderByAsc(ServiceRoute::getId));
    }

    public ServiceRoute detail(GovIdReq req) {
        ServiceRoute route = getOrThrow(req.getId());
        Agent agent = agentService.getOrThrow(route.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        return route;
    }

    public ServiceRoute getOrThrow(Long id) {
        ServiceRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "服务路由不存在: " + id);
        }
        return route;
    }

    /** 运维处置：按 id 将路由置 DRAINED（停流量）。幂等：已 DRAINED 直接返回。供 08 观测模块处置动作调用。 */
    @Transactional
    public ServiceRoute drain(GovIdReq req) {
        ServiceRoute route = getOrThrow(req.getId());
        Agent agent = agentService.getOrThrow(route.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        if (route.getStatus() == RouteStatus.DRAINED) {
            return route;
        }
        route.setStatus(RouteStatus.DRAINED);
        routeMapper.updateById(route);
        auditService.record(MODULE, "route.drain", "gov_service_route", route.getId(),
                Map.of("agentId", route.getAgentId(), "env", route.getEnv(),
                        "routeRevision", route.getRouteRevision()));
        return route;
    }

    /** 项目内全部 Agent id（权限 scope 已由切面校验项目成员身份） */
    List<Long> agentIdsOfProject(Long projectId) {
        com.bosc.agentops.agentdev.dto.AgentListReq listReq = new com.bosc.agentops.agentdev.dto.AgentListReq();
        listReq.setProjectId(projectId);
        return agentService.list(listReq).stream().map(Agent::getId).toList();
    }

    /** 当前 ACTIVE 路由（运行时路由解析用）；version 非空时按版本过滤 */
    ServiceRoute findActiveRoute(Long agentId, String env, String version) {
        return routeMapper.selectOne(new LambdaQueryWrapper<ServiceRoute>()
                .eq(ServiceRoute::getAgentId, agentId)
                .eq(ServiceRoute::getEnv, env)
                .eq(ServiceRoute::getStatus, RouteStatus.ACTIVE)
                .eq(version != null && !version.isBlank(), ServiceRoute::getAgentVersion, version)
                .orderByDesc(ServiceRoute::getRouteRevision)
                .last("LIMIT 1"));
    }

    private Deployment findRunningDeployment(Long agentId, String env) {
        List<Long> releaseIds = releaseMapper.selectList(new LambdaQueryWrapper<Release>()
                        .eq(Release::getAgentId, agentId).select(Release::getId))
                .stream().map(Release::getId).toList();
        if (releaseIds.isEmpty()) {
            return null;
        }
        List<Deployment> running = deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                .in(Deployment::getReleaseId, releaseIds)
                .eq(Deployment::getStatus, DeploymentStatus.RUNNING)
                .orderByDesc(Deployment::getId));
        for (Deployment deployment : running) {
            DeployTarget target = deployTargetMapper.selectById(deployment.getTargetId());
            if (target != null && target.getEnv() != null && target.getEnv().name().equals(env)) {
                return deployment;
            }
        }
        return null;
    }

    private int nextRevision(Long agentId, String env) {
        List<ServiceRoute> routes = routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                .eq(ServiceRoute::getAgentId, agentId)
                .eq(ServiceRoute::getEnv, env));
        return routes.stream().mapToInt(r -> r.getRouteRevision() == null ? 0 : r.getRouteRevision())
                .max().orElse(0) + 1;
    }
}
