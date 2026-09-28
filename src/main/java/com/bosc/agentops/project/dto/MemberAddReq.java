package com.bosc.agentops.project.dto;

import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.entity.SubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MemberAddReq {

    @NotNull
    private SubjectType subjectType;

    @NotBlank
    private String subjectId;

    @NotNull
    private Role role;

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
