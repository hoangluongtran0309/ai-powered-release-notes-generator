package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider refused the submitted access token, so it is never stored. */
public class SourceTokenRejectedException extends LocalizedException {

    private final String code;

    SourceTokenRejectedException(SourceType sourceType) {
        super("error." + code(sourceType));
        this.code = code(sourceType);
    }

    public String code() {
        return code;
    }

    private static String code(SourceType sourceType) {
        return switch (sourceType) {
            case GITHUB -> "github_token_rejected";
            case GITLAB -> "gitlab_token_rejected";
            case LINEAR -> "linear_token_rejected";
            case JIRA -> "jira_token_rejected";
        };
    }
}
