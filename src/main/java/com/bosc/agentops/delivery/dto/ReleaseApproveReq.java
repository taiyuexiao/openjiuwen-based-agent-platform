package com.bosc.agentops.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批放行：approvalRef 为行内审批单号（占位），必填（拟办分离：approve 权限点独立于 release）。
 */
public class ReleaseApproveReq {

    @NotNull
    private Long projectId;

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 64)
    private String approvalRef;

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

    public String getApprovalRef() {
        return approvalRef;
    }

    public void setApprovalRef(String approvalRef) {
        this.approvalRef = approvalRef;
    }
}
