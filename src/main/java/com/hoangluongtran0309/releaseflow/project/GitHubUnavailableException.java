package com.hoangluongtran0309.releaseflow.project;

public class GitHubUnavailableException extends RuntimeException {

    public GitHubUnavailableException() {
        super("GitHub could not be reached to check the token. Try again later.");
    }
}
