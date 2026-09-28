package com.bosc.agentops.assethub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 把选定 operation 转换为 MCP_TOOL 资产草稿（DRAFT，definition 内含 source_http_api_id 回链）。 */
public class HttpApiConvertReq {

    @NotNull
    private Long projectId;

    /** 待转换的工具名（工具草案的 name，即 operationId 或 method+path） */
    @NotEmpty
    private List<String> operations;

    /** 可选：生成的 MCP_TOOL 直接归属该 MCP_SERVICE */
    private Long mcpServiceId;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public List<String> getOperations() {
        return operations;
    }

    public void setOperations(List<String> operations) {
        this.operations = operations;
    }

    public Long getMcpServiceId() {
        return mcpServiceId;
    }

    public void setMcpServiceId(Long mcpServiceId) {
        this.mcpServiceId = mcpServiceId;
    }
}
