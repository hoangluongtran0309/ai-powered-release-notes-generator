package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * The audience-neutral facts of one change, written once by AI in the Organization's
 * output language. {@code migrationStep} is empty when nothing is needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeutralSummary(String whatChanged, String whyChanged, String technicalDetail, String migrationStep) {

    public NeutralSummary {
        Objects.requireNonNull(whatChanged, "whatChanged must not be null");
        Objects.requireNonNull(whyChanged, "whyChanged must not be null");
        Objects.requireNonNull(technicalDetail, "technicalDetail must not be null");
        Objects.requireNonNull(migrationStep, "migrationStep must not be null");
    }
}
