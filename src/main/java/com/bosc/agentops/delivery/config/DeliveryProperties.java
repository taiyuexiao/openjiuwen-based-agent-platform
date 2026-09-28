package com.bosc.agentops.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交付模块配置。executor：部署执行器选择（local=本地桩 / openjiuwen=openJiuwen agent-runtime HTTP 客户端骨架）；
 * levelProfiles：Agent 等级 → 运行保障参数映射（副本数/健康检查/资源限制），deploy 时按 levelSnapshot 取用。
 */
@ConfigurationProperties(prefix = "agentops.delivery")
public class DeliveryProperties {

    /** local | openjiuwen，默认 local */
    private String executor = "local";

    private final OpenJiuwen openjiuwen = new OpenJiuwen();

    /** key = 等级（P0/P1/P2/P3） */
    private Map<String, LevelProfile> levelProfiles = new LinkedHashMap<>();

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public OpenJiuwen getOpenjiuwen() {
        return openjiuwen;
    }

    public Map<String, LevelProfile> getLevelProfiles() {
        return levelProfiles;
    }

    public void setLevelProfiles(Map<String, LevelProfile> levelProfiles) {
        this.levelProfiles = levelProfiles;
    }

    /** 等级对应的运行保障参数 */
    public static class LevelProfile {
        private int replicas = 1;
        private String cpu;
        private String memory;
        private String healthCheckPath = "/health";
        private int healthCheckIntervalSeconds = 30;

        public int getReplicas() {
            return replicas;
        }

        public void setReplicas(int replicas) {
            this.replicas = replicas;
        }

        public String getCpu() {
            return cpu;
        }

        public void setCpu(String cpu) {
            this.cpu = cpu;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getHealthCheckPath() {
            return healthCheckPath;
        }

        public void setHealthCheckPath(String healthCheckPath) {
            this.healthCheckPath = healthCheckPath;
        }

        public int getHealthCheckIntervalSeconds() {
            return healthCheckIntervalSeconds;
        }

        public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
            this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
        }
    }

    public static class OpenJiuwen {
        /** agent-runtime 地址；executor=openjiuwen 时必填，未配置则该执行器构造即报错 */
        private String runtimeBaseUrl;

        public String getRuntimeBaseUrl() {
            return runtimeBaseUrl;
        }

        public void setRuntimeBaseUrl(String runtimeBaseUrl) {
            this.runtimeBaseUrl = runtimeBaseUrl;
        }
    }
}
