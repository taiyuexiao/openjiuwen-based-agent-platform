package com.bosc.agentops.modelknowledge.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 通用目录项 ID 请求（disable 等单 ID 操作复用）。
 */
public class CatalogIdReq {

    @NotNull
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
