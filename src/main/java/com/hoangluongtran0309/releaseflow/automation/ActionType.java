package com.hoangluongtran0309.releaseflow.automation;

/** Where a Rule delivers a release note. */
public enum ActionType {

    GITHUB_RELEASE("GitHub Release"),
    SLACK("Slack"),
    EMAIL("Email");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
