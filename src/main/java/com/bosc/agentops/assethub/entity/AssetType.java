package com.bosc.agentops.assethub.entity;

/**
 * 资产四类型。MCP_TOOL 经 parentAssetId 归属某个 MCP_SERVICE；HTTP_API 为历史接口登记表，可转换为 MCP_TOOL。
 */
public enum AssetType {
    SKILL,
    MCP_SERVICE,
    MCP_TOOL,
    HTTP_API
}
