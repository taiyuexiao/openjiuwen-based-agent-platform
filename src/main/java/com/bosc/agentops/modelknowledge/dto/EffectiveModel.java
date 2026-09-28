package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 项目当前可用模型 + 参数策略合并结果，供 SDK/底座拉取。
 * effectiveParams = 模型默认参数 ← 项目 param_policy 允许范围内的覆盖；
 * policy 外的默认参数键被剔除并列入 removedKeys。
 */
public class EffectiveModel {

    private Long grantId;
    private Long modelServiceId;
    private String modelCode;
    private String displayName;
    private Long providerId;
    private String providerCode;
    private String providerType;
    private String endpoint;
    private String authType;
    private String credentialRef;
    private JsonNode capabilities;
    private Map<String, Object> effectiveParams;
    private List<String> removedKeys;

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public Long getModelServiceId() {
        return modelServiceId;
    }

    public void setModelServiceId(Long modelServiceId) {
        this.modelServiceId = modelServiceId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public JsonNode getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(JsonNode capabilities) {
        this.capabilities = capabilities;
    }

    public Map<String, Object> getEffectiveParams() {
        return effectiveParams;
    }

    public void setEffectiveParams(Map<String, Object> effectiveParams) {
        this.effectiveParams = effectiveParams;
    }

    public List<String> getRemovedKeys() {
        return removedKeys;
    }

    public void setRemovedKeys(List<String> removedKeys) {
        this.removedKeys = removedKeys;
    }
}
