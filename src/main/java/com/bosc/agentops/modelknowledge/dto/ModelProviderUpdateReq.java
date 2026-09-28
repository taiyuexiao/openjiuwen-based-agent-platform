package com.bosc.agentops.modelknowledge.dto;

import com.bosc.agentops.modelknowledge.entity.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ModelProviderUpdateReq {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String endpoint;

    @NotNull
    private AuthType authType;

    /** 密钥引用标识，不存明文 */
    @Size(max = 256)
    private String credentialRef;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
