package com.bosc.agentops.enterprise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.entity.AgentStatus;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.enterprise.config.CmdbProperties;
import com.bosc.agentops.enterprise.connector.CmdbApp;
import com.bosc.agentops.enterprise.connector.CmdbAppStatus;
import com.bosc.agentops.enterprise.connector.CmdbConnector;
import com.bosc.agentops.enterprise.dto.BindReq;
import com.bosc.agentops.enterprise.dto.BindingListReq;
import com.bosc.agentops.enterprise.dto.BindingOperateReq;
import com.bosc.agentops.enterprise.dto.BindingResp;
import com.bosc.agentops.enterprise.entity.AppBinding;
import com.bosc.agentops.enterprise.entity.SyncStatus;
import com.bosc.agentops.enterprise.mapper.AppBindingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 归属应用绑定：bind 时从 CMDB 拉权威信息落快照（fail-closed，CMDB 查无应用或应用非 ACTIVE 即拒绝）；
 * resync 失败（查无应用/已停用）置 FAILED/STALE 但保留最后已知快照；STALE 由读取与门禁按
 * synced_at + agentops.cmdb.stale-threshold-hours 动态判定，不起定时任务改库。
 */
@Service
public class AppBindingService {

    static final String MODULE = "enterprise-integration";

    private final AppBindingMapper bindingMapper;
    private final AgentMapper agentMapper;
    private final AgentService agentService;
    private final CmdbConnector cmdbConnector;
    private final CmdbProperties cmdbProperties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AppBindingService(AppBindingMapper bindingMapper, AgentMapper agentMapper,
                             AgentService agentService, CmdbConnector cmdbConnector,
                             CmdbProperties cmdbProperties, AuditService auditService,
                             ObjectMapper objectMapper) {
        this.bindingMapper = bindingMapper;
        this.agentMapper = agentMapper;
        this.agentService = agentService;
        this.cmdbConnector = cmdbConnector;
        this.cmdbProperties = cmdbProperties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BindingResp bind(BindReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = requireProjectAgent(req.getAgentId(), req.getProjectId());
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "Agent 非 ACTIVE，禁止绑定归属应用: agentId=" + agent.getId() + ", status=" + agent.getStatus());
        }
        CmdbApp app = cmdbConnector.fetchApp(req.getAppCode())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "CMDB 应用不存在: " + req.getAppCode()));
        if (app.getStatus() != CmdbAppStatus.ACTIVE) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "归属应用状态非 ACTIVE，拒绝绑定: appCode=" + app.getAppCode() + ", status=" + app.getStatus());
        }
        if (getByAgentId(req.getAgentId()) != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "Agent 已绑定归属应用，重复绑定需先 unbind: agentId=" + req.getAgentId());
        }
        AppBinding binding = new AppBinding();
        binding.setAgentId(req.getAgentId());
        binding.setAppCode(app.getAppCode());
        binding.setSourceSystem(cmdbConnector.sourceSystem());
        binding.setSyncStatus(SyncStatus.SYNCED);
        binding.setSyncedAt(LocalDateTime.now());
        binding.setSnapshot(snapshotJson(app));
        binding.setBoundBy(userId);
        try {
            bindingMapper.insert(binding);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "Agent 已绑定归属应用，重复绑定需先 unbind: agentId=" + req.getAgentId());
        }
        auditService.record(MODULE, "binding.bind", "app_binding", binding.getId(),
                Map.of("agentId", binding.getAgentId(), "appCode", binding.getAppCode(),
                        "sourceSystem", binding.getSourceSystem()));
        return toResp(binding, null);
    }

    /**
     * 重同步：成功则刷新快照与 synced_at；CMDB 查无应用置 FAILED、应用已停用置 STALE，
     * 两者均不清空旧快照（保留最后已知权威状态）并以 warning 返回明确告警。
     */
    @Transactional
    public BindingResp resync(BindingOperateReq req) {
        requireProjectAgent(req.getAgentId(), req.getProjectId());
        AppBinding binding = getByAgentOrThrow(req.getAgentId());
        var appOpt = cmdbConnector.fetchApp(binding.getAppCode());
        if (appOpt.isEmpty()) {
            binding.setSyncStatus(SyncStatus.FAILED);
            bindingMapper.updateById(binding);
            String warning = "CMDB 查无应用 " + binding.getAppCode()
                    + "，同步状态置 FAILED；已保留最后已知权威快照，请核实应用归属后重试";
            auditService.record(MODULE, "binding.resync", "app_binding", binding.getId(),
                    Map.of("agentId", binding.getAgentId(), "appCode", binding.getAppCode(),
                            "syncStatus", SyncStatus.FAILED.name()));
            return toResp(binding, warning);
        }
        CmdbApp app = appOpt.get();
        if (app.getStatus() != CmdbAppStatus.ACTIVE) {
            binding.setSyncStatus(SyncStatus.STALE);
            bindingMapper.updateById(binding);
            String warning = "归属应用已停用（" + app.getStatus() + "），同步状态置 STALE；"
                    + "已保留最后已知权威快照，请确认应用状态或改绑其他应用";
            auditService.record(MODULE, "binding.resync", "app_binding", binding.getId(),
                    Map.of("agentId", binding.getAgentId(), "appCode", binding.getAppCode(),
                            "syncStatus", SyncStatus.STALE.name()));
            return toResp(binding, warning);
        }
        binding.setSnapshot(snapshotJson(app));
        binding.setSyncStatus(SyncStatus.SYNCED);
        binding.setSyncedAt(LocalDateTime.now());
        binding.setSourceSystem(cmdbConnector.sourceSystem());
        bindingMapper.updateById(binding);
        auditService.record(MODULE, "binding.resync", "app_binding", binding.getId(),
                Map.of("agentId", binding.getAgentId(), "appCode", binding.getAppCode(),
                        "syncStatus", SyncStatus.SYNCED.name()));
        return toResp(binding, null);
    }

    @Transactional
    public BindingResp unbind(BindingOperateReq req) {
        requireProjectAgent(req.getAgentId(), req.getProjectId());
        AppBinding binding = getByAgentOrThrow(req.getAgentId());
        bindingMapper.deleteById(binding.getId());
        auditService.record(MODULE, "binding.unbind", "app_binding", binding.getId(),
                Map.of("agentId", binding.getAgentId(), "appCode", binding.getAppCode()));
        return toResp(binding, null);
    }

    public BindingResp detail(BindingOperateReq req) {
        requireProjectAgent(req.getAgentId(), req.getProjectId());
        return toResp(getByAgentOrThrow(req.getAgentId()), null);
    }

    public List<BindingResp> list(BindingListReq req) {
        List<Agent> agents = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getProjectId, req.getProjectId())
                .eq(req.getAgentId() != null, Agent::getId, req.getAgentId()));
        if (agents.isEmpty()) {
            return List.of();
        }
        List<Long> agentIds = agents.stream().map(Agent::getId).toList();
        return bindingMapper.selectList(new LambdaQueryWrapper<AppBinding>()
                        .in(AppBinding::getAgentId, agentIds)
                        .orderByAsc(AppBinding::getId))
                .stream().map(binding -> toResp(binding, null)).toList();
    }

    public AppBinding getByAgentId(Long agentId) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
    }

    public AppBinding getByAgentOrThrow(Long agentId) {
        AppBinding binding = getByAgentId(agentId);
        if (binding == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 未绑定归属应用: agentId=" + agentId);
        }
        return binding;
    }

    /** Agent 存在且属于该项目（防跨项目冒用权限） */
    private Agent requireProjectAgent(Long agentId, Long projectId) {
        Agent agent = agentService.getOrThrow(agentId);
        agentService.requireOwnerProject(agent, projectId);
        return agent;
    }

    private BindingResp toResp(AppBinding binding, String warning) {
        return BindingResp.of(binding, effectiveStatus(binding, cmdbProperties.getStaleThresholdHours()), warning);
    }

    /** STALE 动态判定：SYNCED 且 synced_at 超过阈值未重同步 → 有效状态视为 STALE（不改库） */
    public static SyncStatus effectiveStatus(AppBinding binding, long staleThresholdHours) {
        if (binding.getSyncStatus() == SyncStatus.SYNCED && binding.getSyncedAt() != null
                && binding.getSyncedAt().isBefore(LocalDateTime.now().minusHours(staleThresholdHours))) {
            return SyncStatus.STALE;
        }
        return binding.getSyncStatus();
    }

    /** 同步时刻权威信息全量快照（appCode/appName/owner/bizDomain/appLevel） */
    private String snapshotJson(CmdbApp app) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("appCode", app.getAppCode());
        snapshot.put("appName", app.getAppName());
        snapshot.put("owner", app.getOwner());
        snapshot.put("bizDomain", app.getBizDomain());
        snapshot.put("appLevel", app.getAppLevel());
        return snapshot.toString();
    }
}
