package com.bosc.agentops.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.entity.AccessMode;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.delivery.config.DeliveryProperties;
import com.bosc.agentops.delivery.dto.ReleaseApproveReq;
import com.bosc.agentops.delivery.dto.ReleaseCreateReq;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.dto.ReleaseListReq;
import com.bosc.agentops.delivery.entity.AgentLevel;
import com.bosc.agentops.delivery.entity.Artifact;
import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.DeployTargetStatus;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.entity.DeploymentStatus;
import com.bosc.agentops.delivery.entity.HealthStatus;
import com.bosc.agentops.delivery.entity.Release;
import com.bosc.agentops.delivery.entity.ReleaseStatus;
import com.bosc.agentops.delivery.executor.DeployPlan;
import com.bosc.agentops.delivery.executor.DeploymentExecutor;
import com.bosc.agentops.delivery.executor.DeploymentExecutorResolver;
import com.bosc.agentops.delivery.executor.DeploymentResult;
import com.bosc.agentops.delivery.mapper.DeploymentMapper;
import com.bosc.agentops.delivery.mapper.ReleaseMapper;
import com.bosc.agentops.enterprise.gate.AppBindingGate;
import com.bosc.agentops.enterprise.gate.GateResult;
import com.bosc.agentops.project.entity.EnvType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布流程状态机（唯一事实源在平台，执行器只执行动作）：
 * DRAFT →（gate）→ GATED →（approve，approvalRef 必填）→ APPROVED →（deploy）→ DEPLOYING
 * →（健康检查通过）→ RUNNING；deploy 失败 → FAILED；rollback 生成指向上一 RUNNING 组合的新
 * Release（重新走 gate→approve→deploy），旧 Release 置 ROLLED_BACK。非法迁移一律 40902。
 * gate 只检查不放行：四项检查（归属/制品完整/等级有效/目标合法）聚合成 gateResult JSON 落库，
 * 无论全过与否都停留 GATED，由 approve 依据 gateResult.passed 判定放行。
 */
@Service
public class ReleaseService {

    static final String MODULE = "delivery";

    private final ReleaseMapper releaseMapper;
    private final DeploymentMapper deploymentMapper;
    private final AgentService agentService;
    private final ArtifactService artifactService;
    private final DeployTargetService deployTargetService;
    private final AgentLevelService agentLevelService;
    private final AppBindingGate appBindingGate;
    private final DeploymentExecutorResolver executorResolver;
    private final DeliveryProperties deliveryProperties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ReleaseService(ReleaseMapper releaseMapper, DeploymentMapper deploymentMapper,
                          AgentService agentService, ArtifactService artifactService,
                          DeployTargetService deployTargetService, AgentLevelService agentLevelService,
                          AppBindingGate appBindingGate, DeploymentExecutorResolver executorResolver,
                          DeliveryProperties deliveryProperties, AuditService auditService,
                          ObjectMapper objectMapper) {
        this.releaseMapper = releaseMapper;
        this.deploymentMapper = deploymentMapper;
        this.agentService = agentService;
        this.artifactService = artifactService;
        this.deployTargetService = deployTargetService;
        this.agentLevelService = agentLevelService;
        this.appBindingGate = appBindingGate;
        this.executorResolver = executorResolver;
        this.deliveryProperties = deliveryProperties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Release create(ReleaseCreateReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        Artifact artifact = artifactService.getOrThrow(req.getArtifactId());
        if (!artifact.getAgentId().equals(agent.getId())
                || !artifact.getAgentVersion().equals(req.getAgentVersion())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "制品与 Agent/版本不一致: artifactId=" + artifact.getId()
                            + " 属于 agentId=" + artifact.getAgentId() + ", version=" + artifact.getAgentVersion());
        }
        DeployTarget target = deployTargetService.getOrThrow(req.getTargetId());
        Release release = new Release();
        release.setAgentId(agent.getId());
        release.setAgentVersion(req.getAgentVersion());
        release.setArtifactId(artifact.getId());
        release.setTargetId(target.getId());
        AgentLevel effective = agentLevelService.getEffective(agent.getId());
        release.setLevelSnapshot(effective == null ? null : effective.getLevel().name());
        release.setStatus(ReleaseStatus.DRAFT);
        release.setCreatedBy(userId);
        releaseMapper.insert(release);
        auditService.record(MODULE, "release.create", "delivery_release", release.getId(),
                Map.of("agentId", agent.getId(), "agentVersion", release.getAgentVersion(),
                        "artifactId", artifact.getId(), "targetId", target.getId()));
        return release;
    }

    /** 发布门禁：聚合四项检查结果落 gateResult，无论通过与否都停留 GATED（可修复后重新 gate） */
    @Transactional
    public Release gate(DeliveryIdReq req) {
        Release release = requireReleaseInProject(req.getId(), req.getProjectId());
        requireStatus(release, "gate", ReleaseStatus.DRAFT, ReleaseStatus.GATED);
        Agent agent = agentService.getOrThrow(release.getAgentId());
        Artifact artifact = artifactService.getOrThrow(release.getArtifactId());

        List<CheckResult> checks = new ArrayList<>();
        // 1. 归属门禁（09 模块 SPI）
        GateResult binding = appBindingGate.validateForRelease(agent.getId());
        checks.add(new CheckResult("appBinding", binding.isPassed(), binding.getReasons()));
        // 2. 制品完整
        List<String> missing = ArtifactService.missingFields(artifact);
        checks.add(new CheckResult("artifact", missing.isEmpty(),
                missing.stream().map(f -> "制品缺少字段: " + f).toList()));
        // 3. 等级有效
        AgentLevel level = agentLevelService.getEffective(agent.getId());
        checks.add(new CheckResult("agentLevel", level != null,
                level == null ? List.of("Agent 无生效中的分级结论（需外部评审后 confirm）") : List.of()));
        // 4. 目标合法：属于项目可选目标集合 且 PROD 时等级在 allowedAgentLevels 内
        checks.add(checkTarget(agent, release, level));

        boolean passed = checks.stream().allMatch(CheckResult::passed);
        release.setGateResult(gateResultJson(checks, passed));
        if (passed && level != null) {
            release.setLevelSnapshot(level.getLevel().name());
        }
        ReleaseStatus from = release.getStatus();
        release.setStatus(ReleaseStatus.GATED);
        releaseMapper.updateById(release);
        auditService.record(MODULE, "release.gate", "delivery_release", release.getId(),
                Map.of("passed", passed, "fromStatus", from.name(), "toStatus", ReleaseStatus.GATED.name()),
                passed ? AuditService.RESULT_SUCCESS : AuditService.RESULT_FAIL);
        return release;
    }

    /** 审批放行：仅 GATED 且门禁全过；approvalRef 必填（DTO @NotBlank 兜底参数校验） */
    @Transactional
    public Release approve(ReleaseApproveReq req) {
        Release release = requireReleaseInProject(req.getId(), req.getProjectId());
        requireStatus(release, "approve", ReleaseStatus.GATED);
        if (!gatePassed(release)) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "发布门禁未通过，禁止审批放行: releaseId=" + release.getId());
        }
        ReleaseStatus from = release.getStatus();
        release.setStatus(ReleaseStatus.APPROVED);
        release.setApprovalRef(req.getApprovalRef());
        releaseMapper.updateById(release);
        auditService.record(MODULE, "release.approve", "delivery_release", release.getId(),
                Map.of("approvalRef", req.getApprovalRef(),
                        "fromStatus", from.name(), "toStatus", ReleaseStatus.APPROVED.name()));
        return release;
    }

    /**
     * 部署执行：APPROVED → DEPLOYING →（执行器部署 + 健康检查通过）→ RUNNING，失败 → FAILED。
     * 运行保障参数按 levelSnapshot 从 agentops.delivery.level-profiles 映射。
     * HOSTED 模式不走执行器，仅登记 Deployment（executor=HOSTED_EXTERNAL，健康状态 UNKNOWN）。
     * 部署成功后停掉同 Agent 其他 RUNNING 部署（目标切换/回滚的旧实例）。
     */
    @Transactional
    public Release deploy(DeliveryIdReq req) {
        String userId = RequestContext.currentUserId();
        Release release = requireReleaseInProject(req.getId(), req.getProjectId());
        requireStatus(release, "deploy", ReleaseStatus.APPROVED);
        Agent agent = agentService.getOrThrow(release.getAgentId());
        DeployTarget target = deployTargetService.getOrThrow(release.getTargetId());
        transition(release, ReleaseStatus.DEPLOYING, "release.deploy", Map.of());

        Deployment deployment = new Deployment();
        deployment.setReleaseId(release.getId());
        deployment.setTargetId(target.getId());
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setHealthStatus(HealthStatus.UNKNOWN);
        deployment.setCreatedBy(userId);

        if (agent.getAccessMode() == AccessMode.HOSTED) {
            // 托管模式：实例运行在外部，平台只登记，不经过执行器
            deployment.setExecutor(DeploymentExecutorResolver.HOSTED_EXTERNAL);
            deployment.setInstanceUrl(agent.getRuntimeEndpoint());
            deployment.setStatus(DeploymentStatus.RUNNING);
            deployment.setStartedAt(LocalDateTime.now());
            deploymentMapper.insert(deployment);
            transition(release, ReleaseStatus.RUNNING, "release.running",
                    Map.of("deploymentId", deployment.getId(), "executor", DeploymentExecutorResolver.HOSTED_EXTERNAL));
            stopOtherRunningDeployments(agent.getId(), deployment.getId());
            return release;
        }

        deployment.setExecutor(executorResolver.current().name());
        deploymentMapper.insert(deployment);
        DeliveryProperties.LevelProfile profile = resolveProfile(release);
        DeployPlan plan = new DeployPlan(deployment.getId(), release.getLevelSnapshot(),
                profile.getReplicas(), profile.getCpu(), profile.getMemory(),
                profile.getHealthCheckPath(), profile.getHealthCheckIntervalSeconds());
        deployment.setReplicas(profile.getReplicas());

        DeploymentExecutor executor = executorResolver.current();
        DeploymentResult result = executor.deploy(release, target, plan);
        if (!result.isSuccess()) {
            failDeploy(release, deployment, "执行器部署失败: " + result.getMessage());
            return release;
        }
        deployment.setInstanceUrl(result.getInstanceUrl());
        deployment.setStartedAt(LocalDateTime.now());
        HealthStatus health = executor.healthCheck(deployment.getId());
        deployment.setHealthStatus(health);
        if (health != HealthStatus.HEALTHY) {
            failDeploy(release, deployment, "健康检查未通过: " + health);
            return release;
        }
        deployment.setStatus(DeploymentStatus.RUNNING);
        deploymentMapper.updateById(deployment);
        transition(release, ReleaseStatus.RUNNING, "release.running",
                Map.of("deploymentId", deployment.getId(), "executor", executor.name()));
        stopOtherRunningDeployments(agent.getId(), deployment.getId());
        return release;
    }

    /**
     * 回滚：当前 Release 必须为 RUNNING；生成指向上一个 RUNNING Release（artifact+target+level 快照）
     * 的新 Release（DRAFT，重新走 gate→approve→deploy），旧 Release 置 ROLLED_BACK。不做原地修改。
     */
    @Transactional
    public Release rollback(DeliveryIdReq req) {
        String userId = RequestContext.currentUserId();
        Release current = requireReleaseInProject(req.getId(), req.getProjectId());
        requireStatus(current, "rollback", ReleaseStatus.RUNNING);
        Release previous = releaseMapper.selectOne(new LambdaQueryWrapper<Release>()
                .eq(Release::getAgentId, current.getAgentId())
                .eq(Release::getStatus, ReleaseStatus.RUNNING)
                .ne(Release::getId, current.getId())
                .orderByDesc(Release::getId)
                .last("LIMIT 1"));
        if (previous == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "不存在可回滚到的 RUNNING 发布单: agentId=" + current.getAgentId());
        }
        transition(current, ReleaseStatus.ROLLED_BACK, "release.rollback",
                Map.of("rollbackToReleaseId", previous.getId()));

        Release rollbackRelease = new Release();
        rollbackRelease.setAgentId(previous.getAgentId());
        rollbackRelease.setAgentVersion(previous.getAgentVersion());
        rollbackRelease.setArtifactId(previous.getArtifactId());
        rollbackRelease.setTargetId(previous.getTargetId());
        rollbackRelease.setLevelSnapshot(previous.getLevelSnapshot());
        rollbackRelease.setRollbackOf(current.getId());
        rollbackRelease.setStatus(ReleaseStatus.DRAFT);
        rollbackRelease.setCreatedBy(userId);
        releaseMapper.insert(rollbackRelease);
        auditService.record(MODULE, "release.create", "delivery_release", rollbackRelease.getId(),
                Map.of("rollbackOf", current.getId(), "sourceReleaseId", previous.getId(),
                        "artifactId", previous.getArtifactId(), "targetId", previous.getTargetId()));
        return rollbackRelease;
    }

    public Release detail(DeliveryIdReq req) {
        return requireReleaseInProject(req.getId(), req.getProjectId());
    }

    public List<Release> list(ReleaseListReq req) {
        AgentListReq agentListReq = new AgentListReq();
        agentListReq.setProjectId(req.getProjectId());
        List<Agent> agents = agentService.list(agentListReq);
        if (agents.isEmpty()) {
            return List.of();
        }
        return releaseMapper.selectList(new LambdaQueryWrapper<Release>()
                .in(Release::getAgentId, agents.stream().map(Agent::getId).toList())
                .eq(req.getAgentId() != null, Release::getAgentId, req.getAgentId())
                .eq(req.getStatus() != null, Release::getStatus, req.getStatus())
                .orderByAsc(Release::getId));
    }

    public Release getOrThrow(Long id) {
        Release release = releaseMapper.selectById(id);
        if (release == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "发布单不存在: " + id);
        }
        return release;
    }

    /** 项目下全部发布单（不做状态过滤，供部署实例列表关联查询） */
    public List<Release> listAllByProject(Long projectId) {
        ReleaseListReq req = new ReleaseListReq();
        req.setProjectId(projectId);
        return list(req);
    }

    /** 发布单存在且其 Agent 属于该项目（防跨项目冒用权限） */
    public Release requireReleaseInProject(Long id, Long projectId) {
        Release release = getOrThrow(id);
        Agent agent = agentService.getOrThrow(release.getAgentId());
        agentService.requireOwnerProject(agent, projectId);
        return release;
    }

    // ---------- 内部 ----------

    private record CheckResult(String name, boolean passed, List<String> reasons) {
    }

    /** 门禁第 4 项：目标存在/ACTIVE/属于项目可选集合；PROD 目标要求等级在 allowedAgentLevels 内 */
    private CheckResult checkTarget(Agent agent, Release release, AgentLevel level) {
        List<String> reasons = new ArrayList<>();
        DeployTarget target;
        try {
            target = deployTargetService.getOrThrow(release.getTargetId());
        } catch (BizException e) {
            return new CheckResult("deployTarget", false, List.of("部署目标不存在: " + release.getTargetId()));
        }
        if (target.getStatus() != DeployTargetStatus.ACTIVE) {
            reasons.add("部署目标非 ACTIVE: " + target.getCode() + ", status=" + target.getStatus());
        }
        if (!deployTargetService.isProjectTarget(agent.getProjectId(), target.getId())) {
            reasons.add("部署目标不在项目可选集合内: targetId=" + target.getId()
                    + ", projectId=" + agent.getProjectId());
        }
        if (target.getEnv() == EnvType.PROD) {
            if (level == null) {
                reasons.add("PROD 目标要求 Agent 具备生效分级结论");
            } else {
                List<String> allowed = parseAllowedLevels(target);
                if (!allowed.contains(level.getLevel().name())) {
                    reasons.add("等级 " + level.getLevel().name() + " 不在 PROD 目标可部署等级内: " + allowed);
                }
            }
        }
        return new CheckResult("deployTarget", reasons.isEmpty(), reasons);
    }

    private List<String> parseAllowedLevels(DeployTarget target) {
        if (JsonSupport.isBlank(target.getAllowedAgentLevels())) {
            return List.of();
        }
        try {
            List<String> levels = new ArrayList<>();
            objectMapper.readTree(target.getAllowedAgentLevels())
                    .forEach(node -> levels.add(node.asText()));
            return levels;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String gateResultJson(List<CheckResult> checks, boolean passed) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("passed", passed);
        result.put("gatedAt", LocalDateTime.now().toString());
        ArrayNode checkNodes = result.putArray("checks");
        for (CheckResult check : checks) {
            ObjectNode node = checkNodes.addObject();
            node.put("name", check.name());
            node.put("passed", check.passed());
            ArrayNode reasons = node.putArray("reasons");
            check.reasons().forEach(reasons::add);
        }
        return JsonSupport.toJson(objectMapper, result);
    }

    private boolean gatePassed(Release release) {
        if (JsonSupport.isBlank(release.getGateResult())) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(release.getGateResult());
            JsonNode passed = node.get("passed");
            return passed != null && passed.asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private DeliveryProperties.LevelProfile resolveProfile(Release release) {
        String level = release.getLevelSnapshot();
        DeliveryProperties.LevelProfile profile = level == null
                ? null : deliveryProperties.getLevelProfiles().get(level);
        if (profile == null) {
            // 无等级快照或等级无保障配置：属平台配置错误，抛错回滚（停留在 APPROVED，可修复配置后重试）
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "等级 " + level + " 无对应运行保障配置（agentops.delivery.level-profiles）: releaseId="
                            + release.getId());
        }
        return profile;
    }

    private void failDeploy(Release release, Deployment deployment, String reason) {
        deployment.setStatus(DeploymentStatus.FAILED);
        deploymentMapper.updateById(deployment);
        transition(release, ReleaseStatus.FAILED, "release.failed",
                Map.of("deploymentId", deployment.getId(), "reason", reason));
    }

    /** 停掉同 Agent 其他 RUNNING 部署（新部署上位后，旧实例/旧目标实例下线） */
    private void stopOtherRunningDeployments(Long agentId, Long keepDeploymentId) {
        List<Long> releaseIds = releaseMapper.selectList(new LambdaQueryWrapper<Release>()
                        .eq(Release::getAgentId, agentId).select(Release::getId))
                .stream().map(Release::getId).toList();
        if (releaseIds.isEmpty()) {
            return;
        }
        List<Deployment> running = deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                .in(Deployment::getReleaseId, releaseIds)
                .eq(Deployment::getStatus, DeploymentStatus.RUNNING)
                .ne(Deployment::getId, keepDeploymentId));
        for (Deployment old : running) {
            if (!DeploymentExecutorResolver.HOSTED_EXTERNAL.equals(old.getExecutor())) {
                executorResolver.byName(old.getExecutor()).stop(old.getId());
            }
            old.setStatus(DeploymentStatus.STOPPED);
            deploymentMapper.updateById(old);
            auditService.record(MODULE, "deployment.stop", "delivery_deployment", old.getId(),
                    Map.of("reason", "superseded", "byDeploymentId", keepDeploymentId));
        }
    }

    private void requireStatus(Release release, String action, ReleaseStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(s -> s == release.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "非法状态迁移: releaseId=" + release.getId() + ", status=" + release.getStatus()
                            + ", 操作=" + action + ", 允许状态=" + Arrays.toString(allowed));
        }
    }

    /** 状态迁移统一入口：落库 + 审计（fromStatus → toStatus） */
    private void transition(Release release, ReleaseStatus to, String action, Map<String, Object> extra) {
        ReleaseStatus from = release.getStatus();
        release.setStatus(to);
        releaseMapper.updateById(release);
        Map<String, Object> detail = new LinkedHashMap<>(extra);
        detail.put("fromStatus", from.name());
        detail.put("toStatus", to.name());
        auditService.record(MODULE, action, "delivery_release", release.getId(), detail);
    }
}
