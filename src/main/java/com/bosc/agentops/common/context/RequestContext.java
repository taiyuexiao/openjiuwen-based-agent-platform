package com.bosc.agentops.common.context;

import com.bosc.agentops.common.auth.AuthUser;

/**
 * 请求上下文（ThreadLocal）：requestId、当前操作人、来源 IP。
 */
public final class RequestContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(Context context) {
        HOLDER.set(context);
    }

    public static Context current() {
        return HOLDER.get();
    }

    public static String currentUserId() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.getUserId();
    }

    public static String currentRequestId() {
        Context ctx = HOLDER.get();
        return ctx == null ? null : ctx.getRequestId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static final class Context {
        private final String requestId;
        private final AuthUser authUser;
        private final String clientIp;

        public Context(String requestId, AuthUser authUser, String clientIp) {
            this.requestId = requestId;
            this.authUser = authUser;
            this.clientIp = clientIp;
        }

        public String getRequestId() {
            return requestId;
        }

        public AuthUser getAuthUser() {
            return authUser;
        }

        public String getUserId() {
            return authUser == null ? null : authUser.getUserId();
        }

        public String getClientIp() {
            return clientIp;
        }
    }
}
