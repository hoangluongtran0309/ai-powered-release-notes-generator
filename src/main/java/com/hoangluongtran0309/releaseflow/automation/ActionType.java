package com.hoangluongtran0309.releaseflow.automation;

/** Where a Rule delivers a release note. */
public enum ActionType {

    GITHUB_RELEASE("GitHub Release"),
    SLACK("Slack"),
    EMAIL("Email"),
    PUBLIC_CHANGELOG("Public changelog"),
    NOTION("Notion"),
    CONFLUENCE("Confluence"),
    MICROSOFT_TEAMS("Microsoft Teams"),
    ZENDESK("Zendesk");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * The bundle key of this ActionType's name, so a page says it in the reader's language.
     * {@link #getLabel()} stays the English wording that recorded evidence keeps.
     */
    public String getLabelKey() {
        return "ui.enum.actionType." + name();
    }
}
