package com.bosc.agentops.agentdev.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 登记 Agent 版本。declaration 为声明清单：
 * {model:{modelCode,params}, prompt:{...}, skills:[{assetId,version}],
 * mcpTools:[{assetId,version}], knowledgeBases:[{kbId}], memory:{...}}。
 * NATIVE 必填；ADAPTED 可空（空则登记后 capabilityDegraded=true）；HOSTED 不允许登记版本。
 */
public class VersionRegisterReq {

    @NotNull
    private Long projectId;

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "版本号必须为 x.y.z 格式")
    private String version;

    private JsonNode declaration;

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

    public JsonNode getDeclaration() {
        return declaration;
    }

    public void setDeclaration(JsonNode declaration) {
        this.declaration = declaration;
    }
}
