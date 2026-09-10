package com.hoangluongtran0309.releaseflow.project;

public class GitHubIntegrationAlreadyConfiguredException extends RuntimeException {

    public GitHubIntegrationAlreadyConfiguredException() {
        super("This project already has a GitHub integration.");
    }
}
