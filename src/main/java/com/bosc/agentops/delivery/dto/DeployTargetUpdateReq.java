package com.bosc.agentops.delivery.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新部署目标：仅允许改名称/集群/命名空间/基础资源/可部署等级；env 与 code 不可变（影响门禁语义）。
 */
public class DeployTargetUpdateReq {

    @NotNull
    private Long id;

    @Size(max = 128)
    private String name;

    @Size(max = 64)
    private String cluster;

    @Size(max = 64)
    private String namespace;

    private JsonNode baseResource;

    private List<@Size(max = 4) String> allowedAgentLevels;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
