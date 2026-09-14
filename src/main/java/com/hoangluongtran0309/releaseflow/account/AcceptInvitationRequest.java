package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AcceptInvitationRequest {

    @NotBlank
    @Size(max = 128)
    private String token;

    @NotBlank
    @Size(max = 120)
    private String displayName;

    @NotBlank
    @ValidPassword
    private String password;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? null : token.strip();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "AcceptInvitationRequest[token=[REDACTED], displayName=%s, password=[REDACTED]]".formatted(displayName);
    }
}
