package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvitationTokenRequest {

    @NotBlank
    @Size(max = 128)
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? null : token.strip();
    }

    @Override
    public String toString() {
        return "InvitationTokenRequest[token=[REDACTED]]";
    }
}
