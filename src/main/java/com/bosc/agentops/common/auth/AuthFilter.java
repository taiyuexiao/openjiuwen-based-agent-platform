package com.bosc.agentops.common.auth;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 认证过滤器：解析 X-Request-Id 与 Authorization: Bearer &lt;token&gt;，
 * 结果写入 RequestContext；/v1/api/** 未认证直接拒绝（fail-closed）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AuthProvider authProvider;
    private final ObjectMapper objectMapper;

    public AuthFilter(AuthProvider authProvider, ObjectMapper objectMapper) {
        this.authProvider = authProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, java.io.IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String path = request.getRequestURI();
        try {
            AuthUser authUser = resolveAuthUser(request);
            if (requiresAuth(path) && authUser == null) {
                writeUnauthorized(response, requestId);
                return;
            }
            RequestContext.set(new RequestContext.Context(requestId, authUser, request.getRemoteAddr()));
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private AuthUser resolveAuthUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return authProvider.resolve(header.substring("Bearer ".length()).trim());
    }

    private boolean requiresAuth(String path) {
        return path != null && path.startsWith("/v1/api/");
    }

    private void writeUnauthorized(HttpServletResponse response, String requestId) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getDefaultMessage());
        body.setRequestId(requestId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
