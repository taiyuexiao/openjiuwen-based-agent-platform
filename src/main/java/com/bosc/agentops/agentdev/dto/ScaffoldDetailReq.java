package com.bosc.agentops.agentdev.dto;

import jakarta.validation.constraints.NotBlank;

/** 脚手架详情查询：按模板 code 定位。 */
public class ScaffoldDetailReq {

    @NotBlank
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
