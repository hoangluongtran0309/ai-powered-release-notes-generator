package com.hoangluongtran0309.releaseflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class GitHubIntegrationRequest {

    @NotBlank(message = "Repository owner is required.")
    @Size(max = 39, message = "Repository owner must not exceed 39 characters.")
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Repository owner contains unsupported characters.")
    private String owner;

    @NotBlank(message = "Repository name is required.")
    @Size(max = 100, message = "Repository name must not exceed 100 characters.")
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "Repository name contains unsupported characters.")
    private String repository;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner == null ? null : owner.strip();
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository == null ? null : repository.strip();
    }
}
