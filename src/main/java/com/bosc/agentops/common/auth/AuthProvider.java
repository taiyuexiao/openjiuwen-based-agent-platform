package com.bosc.agentops.common.auth;

/**
 * 认证适配层 SPI。本地开发用 SimpleTokenAuthProvider，生产替换为行内 IAM 实现。
 *
 * @return 解析失败返回 null
 */
public interface AuthProvider {

    AuthUser resolve(String token);
}
