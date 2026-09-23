package com.hoangluongtran0309.releaseflow.release;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ReleaseStatus {
    DRAFT("Draft"),
    IN_REVIEW("In review"),
    APPROVED("Approved"),
    PUBLISHED("Published");

    private final String label;

    ReleaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * The bundle key of this ReleaseStatus's name, so a page says it in the reader's language.
     * {@link #getLabel()} stays the English wording that recorded evidence keeps.
     */
    public String getLabelKey() {
        return "ui.enum.releaseStatus." + name();
    }

    public String getValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Optional<ReleaseStatus> fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.getValue().equals(value))
                .findFirst();
    }
}
