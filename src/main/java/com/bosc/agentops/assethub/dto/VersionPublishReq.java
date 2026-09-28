package com.bosc.agentops.assethub.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 发布新版本：版本创建即 PUBLISHED 且不可变。 */
public class VersionPublishReq {

    @NotNull
    private Long projectId;

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "版本号必须为 x.y.z 格式")
    private String version;

    /** definition JSON（Skill 清单 / MCP 服务连接配置 / 工具 schema） */
    private JsonNode definition;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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
