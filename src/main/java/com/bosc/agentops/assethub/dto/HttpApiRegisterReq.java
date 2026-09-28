package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册历史 HTTP API：提交 OpenAPI 3.x JSON 字符串，解析出工具定义草案。 */
public class HttpApiRegisterReq {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "仅允许字母、数字、中划线")
    @Size(max = 128)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private Long projectId;

    @Size(max = 1024)
    private String description;

    /** OpenAPI 3.x JSON 原文 */
    @NotBlank
    private String openApiJson;

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

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOpenApiJson() {
        return openApiJson;
    }

    public void setOpenApiJson(String openApiJson) {
        this.openApiJson = openApiJson;
    }
}
