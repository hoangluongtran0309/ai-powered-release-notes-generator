package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider could not be reached, so nothing is decided about the token. */
public class SourceUnavailableException extends RuntimeException {

    private final String code;

    SourceUnavailableException(SourceType sourceType) {
        super(switch (sourceType) {
            case GITHUB -> "GitHub could not be reached to check the token. Try again later.";
            case GITLAB -> "GitLab could not be reached to check the token. Try again later.";
        });
        this.code = switch (sourceType) {
            case GITHUB -> "github_unavailable";
            case GITLAB -> "gitlab_unavailable";
        };
    }

    public String code() {
        return code;
    }
}
