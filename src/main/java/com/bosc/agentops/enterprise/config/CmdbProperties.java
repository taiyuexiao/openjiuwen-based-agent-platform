package com.bosc.agentops.enterprise.config;

import com.bosc.agentops.enterprise.connector.CmdbAppStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CMDB 集成配置。staleThresholdHours：STALE 动态判定阈值（默认 24h）；
 * resyncJobEnabled：定时重同步开关（本期预留，默认关闭）；mockApps：MockCmdbConnector 内置应用表。
 */
@ConfigurationProperties(prefix = "agentops.cmdb")
public class CmdbProperties {

    private long staleThresholdHours = 24;

    /** 定时重同步开关：本期不提供定时任务实现，仅预留配置项 */
    private boolean resyncJobEnabled = false;

    /** key = appCode */
    private Map<String, MockAppEntry> mockApps = new LinkedHashMap<>();

    public long getStaleThresholdHours() {
        return staleThresholdHours;
    }

    public void setStaleThresholdHours(long staleThresholdHours) {
        this.staleThresholdHours = staleThresholdHours;
    }

    public boolean isResyncJobEnabled() {
        return resyncJobEnabled;
    }

    public void setResyncJobEnabled(boolean resyncJobEnabled) {
        this.resyncJobEnabled = resyncJobEnabled;
    }

    public Map<String, MockAppEntry> getMockApps() {
        return mockApps;
    }

    public void setMockApps(Map<String, MockAppEntry> mockApps) {
        this.mockApps = mockApps;
    }

    public static class MockAppEntry {
        private String appName;
        private String owner;
        private String bizDomain;
        private String appLevel;
        private CmdbAppStatus status = CmdbAppStatus.ACTIVE;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getBizDomain() {
            return bizDomain;
        }

        public void setBizDomain(String bizDomain) {
            this.bizDomain = bizDomain;
        }

        public String getAppLevel() {
            return appLevel;
        }

        public void setAppLevel(String appLevel) {
            this.appLevel = appLevel;
        }

        public CmdbAppStatus getStatus() {
            return status;
        }

        public void setStatus(CmdbAppStatus status) {
            this.status = status;
        }
    }
}
