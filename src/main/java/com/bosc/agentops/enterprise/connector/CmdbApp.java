package com.bosc.agentops.enterprise.connector;

/**
 * CMDB 权威应用信息。平台不手工维护这些字段，全部来自 CmdbConnector。
 */
public class CmdbApp {

    /** 行内应用唯一编号 */
    private String appCode;
    private String appName;
    private String owner;
    private String bizDomain;
    /** 应用分级（如 A/B/C），投产门禁要求快照必须含分级 */
    private String appLevel;
    private CmdbAppStatus status;

    public CmdbApp() {
    }

    public CmdbApp(String appCode, String appName, String owner, String bizDomain, String appLevel,
                   CmdbAppStatus status) {
        this.appCode = appCode;
        this.appName = appName;
        this.owner = owner;
        this.bizDomain = bizDomain;
        this.appLevel = appLevel;
        this.status = status;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

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
