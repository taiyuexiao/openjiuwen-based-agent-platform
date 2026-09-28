package com.bosc.agentops.modelknowledge.dto;

import com.bosc.agentops.modelknowledge.entity.AuthType;
import com.bosc.agentops.modelknowledge.entity.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ModelProviderCreateReq {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "仅允许字母、数字、中划线")
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private ProviderType providerType;

    @Size(max = 512)
    private String endpoint;

    @NotNull
    private AuthType authType;

    /** 密钥引用标识，不存明文 */
    @Size(max = 256)
    private String credentialRef;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }
}
