package com.hoangluongtran0309.releaseflow.gitlab;

public class GitLabHostNotAllowedException extends RuntimeException {

    public GitLabHostNotAllowedException(String message) {
        super(message);
    }
}
