package com.bosc.agentops.observability.support;

import com.bosc.agentops.common.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 机器通道（/v1/otlp/**、/v1/alerts/webhook）轻量校验：配置 token（X-Platform-Token 头）。
 * 未配置 agentops.observability.machine-token 时拒绝服务（503）；缺失/不匹配时 401。
 */
@Component
public class MachineTokenGuard {

    public static final String TOKEN_HEADER = "X-Platform-Token";

    private final String machineToken;

    public MachineTokenGuard(@Value("${agentops.observability.machine-token:}") String machineToken) {
        this.machineToken = machineToken;
    }

    public void check(String token) {
        if (machineToken == null || machineToken.isBlank()) {
            throw new MachineChannelException(503, ErrorCode.INTERNAL_ERROR,
                    "机器通道未配置 agentops.observability.machine-token，拒绝服务");
        }
        boolean match = token != null && MessageDigest.isEqual(
                machineToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!match) {
            throw new MachineChannelException(401, ErrorCode.UNAUTHORIZED, "机器通道凭证无效");
        }
    }
}
