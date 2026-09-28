package com.bosc.agentops.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.delivery.dto.DeploymentListReq;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.entity.Deployment;
import com.bosc.agentops.delivery.entity.DeploymentStatus;
import com.bosc.agentops.delivery.entity.Release;
import com.bosc.agentops.delivery.executor.DeploymentExecutorResolver;
import com.bosc.agentops.delivery.mapper.DeploymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 部署实例查询与停止。stop 按 deployment.executor 落库值路由回原执行器；
 * HOSTED_EXTERNAL（外部托管）不经过执行器，仅更新平台登记状态。
 */
@Service
public class DeploymentService {

    private final DeploymentMapper deploymentMapper;
    private final ReleaseService releaseService;
    private final AgentService agentService;
    private final DeploymentExecutorResolver executorResolver;
    private final AuditService auditService;

    public DeploymentService(DeploymentMapper deploymentMapper, ReleaseService releaseService,
                             AgentService agentService, DeploymentExecutorResolver executorResolver,
                             AuditService auditService) {
        this.deploymentMapper = deploymentMapper;
        this.releaseService = releaseService;
        this.agentService = agentService;
        this.executorResolver = executorResolver;
        this.auditService = auditService;
    }

    public Deployment detail(DeliveryIdReq req) {
        Deployment deployment = getOrThrow(req.getId());
        requireProject(deployment, req.getProjectId());
        return deployment;
    }

    public List<Deployment> list(DeploymentListReq req) {
        // 经 Release → Agent 校验项目归属；releaseId 过滤时先校验该 Release 属于项目
        if (req.getReleaseId() != null) {
            releaseService.requireReleaseInProject(req.getReleaseId(), req.getProjectId());
            return deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                    .eq(Deployment::getReleaseId, req.getReleaseId())
                    .orderByAsc(Deployment::getId));
        }
        List<Release> releases = releaseService.listAllByProject(req.getProjectId());
        if (releases.isEmpty()) {
            return List.of();
        }
        return deploymentMapper.selectList(new LambdaQueryWrapper<Deployment>()
                .in(Deployment::getReleaseId, releases.stream().map(Release::getId).toList())
                .orderByAsc(Deployment::getId));
    }

    @Transactional
    public Deployment stop(DeliveryIdReq req) {
        Deployment deployment = getOrThrow(req.getId());
        requireProject(deployment, req.getProjectId());
        if (deployment.getStatus() != DeploymentStatus.RUNNING) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "仅 RUNNING 状态的部署可停止: deploymentId=" + deployment.getId()
                            + ", status=" + deployment.getStatus());
        }
        if (!DeploymentExecutorResolver.HOSTED_EXTERNAL.equals(deployment.getExecutor())) {
            executorResolver.byName(deployment.getExecutor()).stop(deployment.getId());
        }
        deployment.setStatus(DeploymentStatus.STOPPED);
        deploymentMapper.updateById(deployment);
        auditService.record(ReleaseService.MODULE, "deployment.stop", "delivery_deployment",
                deployment.getId(), Map.of("reason", "manual"));
        return deployment;
    }

    public Deployment getOrThrow(Long id) {
        Deployment deployment = deploymentMapper.selectById(id);
        if (deployment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部署实例不存在: " + id);
        }
        return deployment;
    }

    private void requireProject(Deployment deployment, Long projectId) {
        Release release = releaseService.getOrThrow(deployment.getReleaseId());
        Agent agent = agentService.getOrThrow(release.getAgentId());
        agentService.requireOwnerProject(agent, projectId);
    }
}
