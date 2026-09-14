package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class GitHubTokenRequest {

    @NotBlank(message = "Access token is required.")
    @Size(max = 255, message = "Access token must not exceed 255 characters.")
    @Pattern(regexp = "\\S*", message = "Access token must not contain spaces.")
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
        return "GitHubTokenRequest[token=***]";
    }
}
