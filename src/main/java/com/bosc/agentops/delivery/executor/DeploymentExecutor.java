package com.bosc.agentops.delivery.executor;

import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.HealthStatus;
import com.bosc.agentops.delivery.entity.Release;

/**
 * 部署执行器 SPI：平台 Release 状态机是唯一事实源，执行器只执行动作。
 * name() 值落库到 deployment.executor，用于 stop 时路由回同一执行器。
 */
public interface DeploymentExecutor {

    /** 执行器标识，如 LOCAL_STUB / OPENJIUWEN_RUNTIME */
    String name();

    DeploymentResult deploy(Release release, DeployTarget target, DeployPlan plan);

    void stop(Long deploymentId);

    HealthStatus healthCheck(Long deploymentId);
}
