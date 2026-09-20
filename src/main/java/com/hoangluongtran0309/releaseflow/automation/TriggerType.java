package com.hoangluongtran0309.releaseflow.automation;

/** What makes a Rule fire. */
public enum TriggerType {

    RELEASE_PUBLISHED("Release published"),
    MANUAL("Run by hand"),
    SCHEDULED_CRON("On a schedule"),
    UPCOMING_RELEASE_REMINDER("Before a planned release"),
    EXTERNAL_WEBHOOK("Called by another system");

    private final String label;

    TriggerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Whether ReleaseFlow itself decides when this trigger fires. Nobody reads a
     * schedule's outcome as it happens, so such a Rule may only tell people something.
     */
    public boolean isScheduled() {
        return this == SCHEDULED_CRON || this == UPCOMING_RELEASE_REMINDER;
    }
}
