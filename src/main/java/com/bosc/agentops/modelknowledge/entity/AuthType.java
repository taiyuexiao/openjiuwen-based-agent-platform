package com.bosc.agentops.modelknowledge.entity;

/**
 * Provider 认证方式：CREDENTIAL_REF=凭据引用（密钥进行内密钥系统，平台只存引用），
 * GATEWAY_PASSTHROUGH=经行内模型网关透传。
 */
public enum AuthType {
    CREDENTIAL_REF,
    GATEWAY_PASSTHROUGH
}
