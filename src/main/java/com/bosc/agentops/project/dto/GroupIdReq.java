package com.bosc.agentops.project.dto;

import jakarta.validation.constraints.NotNull;

public class GroupIdReq {

    @NotNull
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
