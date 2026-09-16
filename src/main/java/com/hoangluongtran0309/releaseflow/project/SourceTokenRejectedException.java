package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider refused the submitted access token, so it is never stored. */
public class SourceTokenRejectedException extends RuntimeException {

    private final String code;

    SourceTokenRejectedException(SourceType sourceType) {
        super(switch (sourceType) {
            case GITHUB -> "GitHub did not accept this token for reading the repository's pull requests.";
            case GITLAB -> "GitLab did not accept this token for reading the project.";
        });
        this.code = switch (sourceType) {
            case GITHUB -> "github_token_rejected";
            case GITLAB -> "gitlab_token_rejected";
        };
    }

    public String code() {
        return code;
    }
}
