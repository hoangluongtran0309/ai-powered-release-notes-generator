package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvitationTokenRequest {

    @NotBlank(message = "{validation.invitationToken.required}")
    @Size(max = 128, message = "{validation.invitationToken.tooLong}")
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
