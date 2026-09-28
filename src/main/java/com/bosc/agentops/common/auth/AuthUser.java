package com.bosc.agentops.common.auth;

import java.util.List;

/**
 * 认证后的用户身份。
 */
public class AuthUser {

    private final String userId;
    private final String displayName;
    private final List<String> groupIds;

    public AuthUser(String userId, String displayName, List<String> groupIds) {
        this.userId = userId;
        this.displayName = displayName;
        this.groupIds = groupIds == null ? List.of() : List.copyOf(groupIds);
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getGroupIds() {
        return groupIds;
    }
}
