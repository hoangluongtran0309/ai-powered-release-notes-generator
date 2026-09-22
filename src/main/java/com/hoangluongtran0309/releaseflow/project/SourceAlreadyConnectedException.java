package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/** A project of that provider is already connected somewhere in this Organization. */
public class SourceAlreadyConnectedException extends LocalizedException {

    private final String code;

    SourceAlreadyConnectedException(SourceType sourceType) {
        super("error." + code(sourceType));
        this.code = code(sourceType);
    }

    public String code() {
        return code;
    }

    private static String code(SourceType sourceType) {
        return switch (sourceType) {
            case GITHUB -> "github_repository_already_connected";
            case GITLAB -> "gitlab_project_already_connected";
            case LINEAR -> "linear_team_already_connected";
            case JIRA -> "jira_project_already_connected";
        };
    }
}
