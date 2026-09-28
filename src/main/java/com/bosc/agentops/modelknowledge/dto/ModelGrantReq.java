package com.bosc.agentops.modelknowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * 项目级模型授权请求。paramPolicy 结构：{ "&lt;参数名&gt;": {"value": 覆盖值, "min": 下限, "max": 上限} }，
 * 授权时冻结允许覆盖的参数白名单/上下限。
 */
public class ModelGrantReq {

    @NotNull
    private Long modelServiceId;

    /** 参数策略 JSON；空表示不限制（默认参数全量放行） */
    private JsonNode paramPolicy;

    public Long getModelServiceId() {
        return modelServiceId;
    }

    public void setModelServiceId(Long modelServiceId) {
        this.modelServiceId = modelServiceId;
    }

    public JsonNode getParamPolicy() {
        return paramPolicy;
    }

    public void setParamPolicy(JsonNode paramPolicy) {
        this.paramPolicy = paramPolicy;
    }
}
