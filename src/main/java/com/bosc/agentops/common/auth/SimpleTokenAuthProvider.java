package com.bosc.agentops.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地开发用认证实现：token 即 userId，用户表来自配置 agentops.auth.users。
 */
@ConfigurationProperties(prefix = "agentops.auth")
public class SimpleTokenAuthProvider implements AuthProvider {

    private Map<String, UserEntry> users = new HashMap<>();
    /** 平台管理员 userId 清单（权限矩阵管理等高危平台级操作仅管理员可用） */
    private List<String> admins = List.of();

    @Override
    public AuthUser resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        UserEntry entry = users.get(token);
        if (entry == null) {
            return null;
        }
        return new AuthUser(token, entry.getDisplayName(), entry.getGroupIds());
    }

    /** 平台管理员判定 */
    public boolean isAdmin(String userId) {
        return userId != null && admins.contains(userId);
    }

    public Map<String, UserEntry> getUsers() {
        return users;
    }

    public void setUsers(Map<String, UserEntry> users) {
        this.users = users;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins == null ? List.of() : List.copyOf(admins);
    }

    public static class UserEntry {
        private String displayName;
        private List<String> groupIds = List.of();

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> getGroupIds() {
            return groupIds;
        }

        public void setGroupIds(List<String> groupIds) {
            this.groupIds = groupIds;
        }
    }
}
