package com.bosc.agentops.observability.service;

import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.entity.CallerPolicy;
import com.bosc.agentops.governance.entity.ServiceRoute;
import com.bosc.agentops.governance.service.CallerPolicyService;
import com.bosc.agentops.governance.service.RouteService;
import com.bosc.agentops.observability.dto.DisableRouteReq;
import com.bosc.agentops.observability.dto.RevokeCallerReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 处置动作：不直接操作底座，只调 07 governance 的路由/策略服务（治理动作唯一入口在 governance）。
 * 全部要求 obs:action 权限（切面）且落 module=observability 审计（governance 侧另有其自身审计）。
 */
@Service
public class OpsActionService {

    private final RouteService routeService;
    private final CallerPolicyService callerPolicyService;
    private final AuditService auditService;

    public OpsActionService(RouteService routeService, CallerPolicyService callerPolicyService,
                            AuditService auditService) {
        this.routeService = routeService;
        this.callerPolicyService = callerPolicyService;
        this.auditService = auditService;
    }

    /** 路由停流量：调 governance 把 route 置 DRAINED */
    @Transactional
    public ServiceRoute disableRoute(DisableRouteReq req) {
        GovIdReq govReq = new GovIdReq();
        govReq.setProjectId(req.getProjectId());
        govReq.setId(req.getRouteId());
        ServiceRoute route = routeService.drain(govReq);
        Map<String, Object> detail = new HashMap<>();
        detail.put("routeId", route.getId());
        detail.put("agentId", route.getAgentId());
        detail.put("env", route.getEnv());
        detail.put("reason", req.getReason());
        auditService.record(AlertService.MODULE, "obs.action.disable-route", "gov_service_route",
                route.getId(), detail);
        return route;
    }

    /** 吊销 CallerPolicy：暂停该调用方 */
    @Transactional
    public CallerPolicy revokeCaller(RevokeCallerReq req) {
        GovIdReq govReq = new GovIdReq();
        govReq.setProjectId(req.getProjectId());
        govReq.setId(req.getPolicyId());
        CallerPolicy policy = callerPolicyService.revoke(govReq);
        Map<String, Object> detail = new HashMap<>();
        detail.put("policyId", policy.getId());
        detail.put("callerType", policy.getCallerType().name());
        detail.put("callerId", policy.getCallerId());
        detail.put("agentId", policy.getAgentId());
        detail.put("reason", req.getReason());
        auditService.record(AlertService.MODULE, "obs.action.revoke-caller", "gov_caller_policy",
                policy.getId(), detail);
        return policy;
    }
}
