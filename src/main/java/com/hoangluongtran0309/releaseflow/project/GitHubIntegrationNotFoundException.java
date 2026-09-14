package com.hoangluongtran0309.releaseflow.project;

public class GitHubIntegrationNotFoundException extends RuntimeException {

    public GitHubIntegrationNotFoundException() {
        super("This project has no GitHub integration.");
    }
}
