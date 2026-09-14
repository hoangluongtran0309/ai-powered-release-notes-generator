package com.hoangluongtran0309.releaseflow.project;

public class GitHubTokenRejectedException extends RuntimeException {

    public GitHubTokenRejectedException() {
        super("GitHub did not accept this token for reading the repository's pull requests.");
    }
}
