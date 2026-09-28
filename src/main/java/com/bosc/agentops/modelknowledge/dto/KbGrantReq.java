package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * 项目级知识库授权请求。scope 为授权范围（集合/标签过滤等）。
 */
public class KbGrantReq {

    @NotNull
    private Long kbId;

    /** 授权范围 JSON，如 {"collections":["c1"],"tags":["t1"]}；空表示不限制 */
    private JsonNode scope;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public JsonNode getScope() {
        return scope;
    }

    public void setScope(JsonNode scope) {
        this.scope = scope;
    }
}
