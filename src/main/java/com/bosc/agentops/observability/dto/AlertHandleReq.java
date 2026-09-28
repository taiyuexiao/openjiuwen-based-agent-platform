package com.bosc.agentops.observability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 告警处置登记：处置说明必填，置 HANDLED。
 */
public class AlertHandleReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long id;

    @NotBlank
    private String note;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
