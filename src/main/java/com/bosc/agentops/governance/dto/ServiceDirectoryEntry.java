package com.bosc.agentops.governance.dto;

/**
 * 服务目录条目：对调用方暴露的服务标识/环境/版本/访问地址/认证方式。
 */
public class ServiceDirectoryEntry {

    private Long agentId;
    private String agentCode;
    private String agentName;
    private String env;
    private String agentVersion;
    /** 统一访问入口地址（运行时面调用路径） */
    private String address;
    /** 认证方式：Bearer（管理面 token 或 CallerPolicy 共享密钥） */
    private String authType;

    public ServiceDirectoryEntry() {
    }

    public ServiceDirectoryEntry(Long agentId, String agentCode, String agentName, String env,
                                 String agentVersion, String address, String authType) {
        this.agentId = agentId;
        this.agentCode = agentCode;
        this.agentName = agentName;
        this.env = env;
        this.agentVersion = agentVersion;
        this.address = address;
        this.authType = authType;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }
}
