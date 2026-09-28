package com.bosc.agentops.delivery.executor;

/**
 * 部署计划：按发布单 levelSnapshot 从 agentops.delivery.level-profiles 映射出的运行保障参数
 * （副本数/健康检查/资源限制），连同部署目标一起交给执行器。
 */
public class DeployPlan {

    private final Long deploymentId;
    private final String level;
    private final int replicas;
    private final String cpu;
    private final String memory;
    private final String healthCheckPath;
    private final int healthCheckIntervalSeconds;

    public DeployPlan(Long deploymentId, String level, int replicas, String cpu, String memory,
                      String healthCheckPath, int healthCheckIntervalSeconds) {
        this.deploymentId = deploymentId;
        this.level = level;
        this.replicas = replicas;
        this.cpu = cpu;
        this.memory = memory;
        this.healthCheckPath = healthCheckPath;
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public Long getDeploymentId() {
        return deploymentId;
    }

    public String getLevel() {
        return level;
    }

    public int getReplicas() {
        return replicas;
    }

    public String getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }
}
