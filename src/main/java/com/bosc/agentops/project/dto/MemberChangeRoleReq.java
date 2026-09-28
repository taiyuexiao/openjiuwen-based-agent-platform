package com.bosc.agentops.project.dto;

import com.bosc.agentops.project.entity.Role;
import jakarta.validation.constraints.NotNull;

public class MemberChangeRoleReq extends MemberRemoveReq {

    @NotNull
    private Role role;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
