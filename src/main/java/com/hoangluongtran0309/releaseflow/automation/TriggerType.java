package com.hoangluongtran0309.releaseflow.automation;

/** What makes a Rule fire. Scheduled and webhook triggers arrive in a later slice. */
public enum TriggerType {

    RELEASE_PUBLISHED("Release published"),
    MANUAL("Run by hand");

    private final String label;

    TriggerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
