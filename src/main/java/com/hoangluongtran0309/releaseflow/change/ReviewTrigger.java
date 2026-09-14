package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * One recorded reason a change needs review. The detail is data, such as the matched
 * path, or null when the type says everything.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewTrigger(ReviewTriggerType type, String detail) {

    public ReviewTrigger {
        Objects.requireNonNull(type, "type must not be null");
    }

    static ReviewTrigger sensitivePath(String path) {
        return new ReviewTrigger(ReviewTriggerType.SENSITIVE_PATH, path);
    }

    static ReviewTrigger changedFilesUnavailable() {
        return new ReviewTrigger(ReviewTriggerType.CHANGED_FILES_UNAVAILABLE, null);
    }

    static ReviewTrigger classifierFallback() {
        return new ReviewTrigger(ReviewTriggerType.CLASSIFIER_FALLBACK, null);
    }

    // Not a getter, so it is never written into the stored JSON.
    public String describe() {
        return switch (type) {
            case SENSITIVE_PATH -> "Sensitive file " + detail;
            case CHANGED_FILES_UNAVAILABLE -> "Changed files unavailable";
            case CLASSIFIER_FALLBACK -> "AI classification failed";
        };
    }
}
