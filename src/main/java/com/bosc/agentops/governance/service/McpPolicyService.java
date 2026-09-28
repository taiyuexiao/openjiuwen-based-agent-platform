package com.bosc.agentops.governance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.governance.dto.CheckResp;
import com.bosc.agentops.governance.dto.GovIdReq;
import com.bosc.agentops.governance.dto.McpPolicyGrantReq;
import com.bosc.agentops.governance.dto.McpPolicyListReq;
import com.bosc.agentops.governance.entity.McpInvokePolicy;
import com.bosc.agentops.governance.mapper.McpInvokePolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * MCP 调用策略：Agent → Tool 资产的运行时权限。grant 为 upsert（更新 allowed 取值）；
 * revoke 删除策略行，删除后回到 fail-closed 默认拒绝。
 */
@Service
public class McpPolicyService {

    private final McpInvokePolicyMapper mcpInvokePolicyMapper;
    private final AgentService agentService;
    private final AuditService auditService;

    public McpPolicyService(McpInvokePolicyMapper mcpInvokePolicyMapper, AgentService agentService,
                            AuditService auditService) {
        this.mcpInvokePolicyMapper = mcpInvokePolicyMapper;
        this.agentService = agentService;
        this.auditService = auditService;
    }

    @Transactional
    public McpInvokePolicy grant(McpPolicyGrantReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        String env = req.getEnv().name();

        McpInvokePolicy existing = mcpInvokePolicyMapper.selectOne(new LambdaQueryWrapper<McpInvokePolicy>()
                .eq(McpInvokePolicy::getAgentId, agent.getId())
                .eq(McpInvokePolicy::getAssetId, req.getAssetId())
                .eq(McpInvokePolicy::getEnv, env)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setAllowed(req.getAllowed());
            mcpInvokePolicyMapper.updateById(existing);
            auditService.record(RouteService.MODULE, "mcp-policy.grant", "gov_mcp_invoke_policy",
                    existing.getId(), Map.of("agentId", agent.getId(), "assetId", req.getAssetId(),
                            "env", env, "allowed", req.getAllowed()));
            return existing;
        }

        McpInvokePolicy policy = new McpInvokePolicy();
        policy.setAgentId(agent.getId());
        policy.setAssetId(req.getAssetId());
        policy.setEnv(env);
        policy.setAllowed(req.getAllowed());
        policy.setCreatedBy(userId);
        mcpInvokePolicyMapper.insert(policy);
        auditService.record(RouteService.MODULE, "mcp-policy.grant", "gov_mcp_invoke_policy",
                policy.getId(), Map.of("agentId", agent.getId(), "assetId", req.getAssetId(),
                        "env", env, "allowed", req.getAllowed()));
        return policy;
    }

    @Transactional
    public void revoke(GovIdReq req) {
        McpInvokePolicy policy = mcpInvokePolicyMapper.selectById(req.getId());
        if (policy == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 调用策略不存在: " + req.getId());
        }
        Agent agent = agentService.getOrThrow(policy.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        mcpInvokePolicyMapper.deleteById(policy.getId());
        auditService.record(RouteService.MODULE, "mcp-policy.revoke", "gov_mcp_invoke_policy",
                policy.getId(), Map.of("agentId", policy.getAgentId(), "assetId", policy.getAssetId(),
                        "env", policy.getEnv()));
    }

    public List<McpInvokePolicy> list(McpPolicyListReq req) {
        if (req.getAgentId() != null) {
            Agent agent = agentService.getOrThrow(req.getAgentId());
            agentService.requireOwnerProject(agent, req.getProjectId());
            return mcpInvokePolicyMapper.selectList(new LambdaQueryWrapper<McpInvokePolicy>()
                    .eq(McpInvokePolicy::getAgentId, req.getAgentId())
                    .eq(req.getEnv() != null, McpInvokePolicy::getEnv,
                            req.getEnv() == null ? null : req.getEnv().name())
                    .orderByAsc(McpInvokePolicy::getId));
        }
        com.bosc.agentops.agentdev.dto.AgentListReq listReq = new com.bosc.agentops.agentdev.dto.AgentListReq();
        listReq.setProjectId(req.getProjectId());
        List<Long> agentIds = agentService.list(listReq).stream().map(Agent::getId).toList();
        if (agentIds.isEmpty()) {
            return List.of();
        }
        return mcpInvokePolicyMapper.selectList(new LambdaQueryWrapper<McpInvokePolicy>()
                .in(McpInvokePolicy::getAgentId, agentIds)
                .eq(req.getEnv() != null, McpInvokePolicy::getEnv,
                        req.getEnv() == null ? null : req.getEnv().name())
                .orderByAsc(McpInvokePolicy::getId));
    }

    /** 运行时 PEP 判定：fail-closed，无策略即拒绝；allowed=false 显式拒绝 */
    public CheckResp check(Long agentId, Long toolAssetId, String env) {
        McpInvokePolicy policy = mcpInvokePolicyMapper.selectOne(new LambdaQueryWrapper<McpInvokePolicy>()
                .eq(McpInvokePolicy::getAgentId, agentId)
                .eq(McpInvokePolicy::getAssetId, toolAssetId)
                .eq(McpInvokePolicy::getEnv, env)
                .last("LIMIT 1"));
        if (policy == null) {
            return new CheckResp(false, "无 MCP 调用策略，默认拒绝（fail-closed）: agentId=" + agentId
                    + ", toolAssetId=" + toolAssetId + ", env=" + env);
        }
        if (!Boolean.TRUE.equals(policy.getAllowed())) {
            return new CheckResp(false, "MCP 调用策略显式拒绝: policyId=" + policy.getId());
        }
        return new CheckResp(true, "允许");
    }
}
