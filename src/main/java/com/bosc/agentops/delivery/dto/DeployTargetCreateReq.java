package com.bosc.agentops.delivery.dto;

import com.bosc.agentops.project.entity.EnvType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建部署目标（平台级资源）。baseResource 为基础资源配置 JSON；
 * allowedAgentLevels 为可部署 Agent 等级（P0-P3），仅对 PROD 目标在门禁中强制。
 */
public class DeployTargetCreateReq {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private EnvType env;

    @NotBlank
    @Size(max = 64)
    private String cluster;

    @NotBlank
    @Size(max = 64)
    private String namespace;

    private JsonNode baseResource;

    private List<@Size(max = 4) String> allowedAgentLevels;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EnvType getEnv() {
        return env;
    }

    public void setEnv(EnvType env) {
        this.env = env;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public JsonNode getBaseResource() {
        return baseResource;
    }

    public void setBaseResource(JsonNode baseResource) {
        this.baseResource = baseResource;
    }

    public List<String> getAllowedAgentLevels() {
        return allowedAgentLevels;
    }

    public void setAllowedAgentLevels(List<String> allowedAgentLevels) {
        this.allowedAgentLevels = allowedAgentLevels;
    }
}
