package com.bosc.agentops.assethub.dto;

import com.bosc.agentops.assethub.entity.AssetVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 资产更新。version+definition 为「修改指定版本定义」入口：
 * 已发布（PUBLISHED/OFFLINE）版本的 definition 不可变，服务端一律拒绝，变更只能发新版本。
 */
public class AssetUpdateReq {

    @NotNull
    private Long id;

    @NotNull
    private Long projectId;

    @Size(max = 128)
    private String name;

    @Size(max = 1024)
    private String description;

    private AssetVisibility visibility;

    /** 尝试修改定义的目标版本号 */
    private String version;

    /** 尝试修改的 definition */
    private JsonNode definition;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AssetVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(AssetVisibility visibility) {
        this.visibility = visibility;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public JsonNode getDefinition() {
        return definition;
    }

    public void setDefinition(JsonNode definition) {
        this.definition = definition;
    }
}
