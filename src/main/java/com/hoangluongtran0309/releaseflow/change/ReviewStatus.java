package com.hoangluongtran0309.releaseflow.change;

import java.util.Arrays;
import java.util.Optional;

public enum ReviewStatus {
    NEEDS_REVIEW("needs-review", "Needs review"),
    CLASSIFIED("classified", "Classified"),
    REVIEWED("reviewed", "Reviewed");

    private final String value;
    private final String label;

    ReviewStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /**
     * The bundle key of this ReviewStatus's name, so a page says it in the reader's language.
     * {@link #getLabel()} stays the English wording that recorded evidence keeps.
     */
    public String getLabelKey() {
        return "ui.enum.reviewStatus." + name();
    }

    static Optional<ReviewStatus> fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst();
    }
}
