package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;

/** A project of that provider is already connected somewhere in this Organization. */
public class SourceAlreadyConnectedException extends RuntimeException {

    private final String code;

    SourceAlreadyConnectedException(SourceType sourceType) {
        super(switch (sourceType) {
            case GITHUB -> "This GitHub repository is already connected to another project.";
            case GITLAB -> "This GitLab project is already connected to another project.";
            case LINEAR -> "This Linear team is already connected to another project.";
            case JIRA -> "This Jira project is already connected to another project.";
        });
        this.code = switch (sourceType) {
            case GITHUB -> "github_repository_already_connected";
            case GITLAB -> "gitlab_project_already_connected";
            case LINEAR -> "linear_team_already_connected";
            case JIRA -> "jira_project_already_connected";
        };
    }

    public String code() {
        return code;
    }
}
