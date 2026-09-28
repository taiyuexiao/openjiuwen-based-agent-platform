package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.governance.dto.CheckResp;
import com.bosc.agentops.governance.entity.CallerType;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 间调用鉴权（运行时面）：callerAgent → calleeAgent 是否存在
 * callerType=AGENT 且 callerId=callerAgentCode 的 ACTIVE CallerPolicy。
 */
@Service
public class AgentAuthService {

    private final AgentMapper agentMapper;
    private final CallerPolicyService callerPolicyService;

    public AgentAuthService(AgentMapper agentMapper, CallerPolicyService callerPolicyService) {
        this.agentMapper = agentMapper;
        this.callerPolicyService = callerPolicyService;
    }

    public CheckResp check(String callerAgentCode, String calleeAgentCode, String env) {
        List<Agent> callees = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCode, calleeAgentCode).orderByAsc(Agent::getId));
        if (callees.isEmpty()) {
            return new CheckResp(false, "被调 Agent 不存在: " + calleeAgentCode);
        }
        for (Agent callee : callees) {
            if (callerPolicyService.findActive(CallerType.AGENT, callerAgentCode, callee.getId(), env) != null) {
                return new CheckResp(true, "允许");
            }
        }
        return new CheckResp(false, "无 Agent 间调用策略（fail-closed）: caller=" + callerAgentCode
                + ", callee=" + calleeAgentCode + ", env=" + env);
    }
}
