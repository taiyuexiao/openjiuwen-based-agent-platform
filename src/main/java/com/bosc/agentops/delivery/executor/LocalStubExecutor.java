package com.bosc.agentops.delivery.executor;

import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.HealthStatus;
import com.bosc.agentops.delivery.entity.Release;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地桩执行器（默认）：不真实部署，生成伪 instance_url（http://stub.local/{deploymentId}），
 * 健康检查直接通过（HEALTHY），用于全流程贯通。
 */
@Component
@ConditionalOnProperty(prefix = "agentops.delivery", name = "executor", havingValue = "local", matchIfMissing = true)
public class LocalStubExecutor implements DeploymentExecutor {

    public static final String NAME = "LOCAL_STUB";

    private static final Logger log = LoggerFactory.getLogger(LocalStubExecutor.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DeploymentResult deploy(Release release, DeployTarget target, DeployPlan plan) {
        log.info("[LocalStub] deploy releaseId={} deploymentId={} target={}/{} replicas={}",
                release.getId(), plan.getDeploymentId(), target.getCluster(), target.getNamespace(),
                plan.getReplicas());
        return DeploymentResult.success("http://stub.local/" + plan.getDeploymentId());
    }

    @Override
    public void stop(Long deploymentId) {
        log.info("[LocalStub] stop deploymentId={}", deploymentId);
    }

    @Override
    public HealthStatus healthCheck(Long deploymentId) {
        return HealthStatus.HEALTHY;
    }
}
