package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/** The provider could not be reached, so nothing is decided about the token. */
public class SourceUnavailableException extends LocalizedException {

    private final String code;

    SourceUnavailableException(SourceType sourceType) {
        super("error." + code(sourceType));
        this.code = code(sourceType);
    }

    public String code() {
        return code;
    }

    private static String code(SourceType sourceType) {
        return switch (sourceType) {
            case GITHUB -> "github_unavailable";
            case GITLAB -> "gitlab_unavailable";
            case LINEAR -> "linear_unavailable";
            case JIRA -> "jira_unavailable";
        };
    }
}
