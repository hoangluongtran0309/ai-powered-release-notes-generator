package com.hoangluongtran0309.releaseflow.automation;

/** Where a Rule delivers a release note. */
public enum ActionType {

    GITHUB_RELEASE("GitHub Release"),
    SLACK("Slack"),
    EMAIL("Email"),
    PUBLIC_CHANGELOG("Public changelog"),
    NOTION("Notion"),
    CONFLUENCE("Confluence");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
