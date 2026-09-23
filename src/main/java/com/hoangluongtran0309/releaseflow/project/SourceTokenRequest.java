package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SourceTokenRequest {

    @NotBlank(message = "{validation.token.required}")
    @Size(max = 255, message = "{validation.token.tooLong}")
    @Pattern(regexp = "\\S*", message = "{validation.token.noSpaces}")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? null : token.strip();
    }

    // Keeps the token out of logs and error pages that print the request object.
    @Override
    public String toString() {
        return "SourceTokenRequest[token=***]";
    }
}
