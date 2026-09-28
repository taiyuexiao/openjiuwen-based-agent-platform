package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.governance.dto.ServiceDirectoryEntry;
import com.bosc.agentops.governance.dto.ServiceDirectoryReq;
import com.bosc.agentops.governance.entity.RouteStatus;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.mapper.ServiceRouteMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 服务目录：聚合 Agent + ACTIVE ServiceRoute，对调用方暴露
 * 服务标识/环境/版本/统一访问地址/认证方式（Bearer）。
 */
@Service
public class ServiceDirectoryService {

    /** 统一访问入口路径模板 */
    static final String INVOKE_ADDRESS = "/v1/invoke/%s/%s";
    static final String AUTH_TYPE = "Bearer";

    private final ServiceRouteMapper routeMapper;
    private final AgentService agentService;

    public ServiceDirectoryService(ServiceRouteMapper routeMapper, AgentService agentService) {
        this.routeMapper = routeMapper;
        this.agentService = agentService;
    }

    public List<ServiceDirectoryEntry> list(ServiceDirectoryReq req) {
        AgentListReq agentListReq = new AgentListReq();
        agentListReq.setProjectId(req.getProjectId());
        List<Agent> agents = agentService.list(agentListReq);
        if (agents.isEmpty()) {
            return List.of();
        }
        Map<Long, Agent> agentById = agents.stream()
                .collect(Collectors.toMap(Agent::getId, Function.identity()));
        List<ServiceRoute> activeRoutes = routeMapper.selectList(new LambdaQueryWrapper<ServiceRoute>()
                .in(ServiceRoute::getAgentId, agentById.keySet())
                .eq(ServiceRoute::getStatus, RouteStatus.ACTIVE)
                .eq(req.getEnv() != null, ServiceRoute::getEnv,
                        req.getEnv() == null ? null : req.getEnv().name())
                .orderByAsc(ServiceRoute::getId));
        return activeRoutes.stream()
                .map(route -> {
                    Agent agent = agentById.get(route.getAgentId());
                    return new ServiceDirectoryEntry(agent.getId(), agent.getCode(), agent.getName(),
                            route.getEnv(), route.getAgentVersion(),
                            String.format(INVOKE_ADDRESS, agent.getCode(), route.getEnv()), AUTH_TYPE);
                })
                .toList();
    }
}
