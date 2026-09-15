package com.hoangluongtran0309.releaseflow.change;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    static ReviewTrigger categorySuggestion(String code) {
        return new ReviewTrigger(ReviewTriggerType.CATEGORY_SUGGESTION_PENDING, code);
    }

    static ReviewTrigger contextInsufficient(List<String> reasons) {
        return new ReviewTrigger(ReviewTriggerType.CONTEXT_INSUFFICIENT, reasons.isEmpty() ? null : String.join(", ", reasons));
    }

    static ReviewTrigger duplicateCandidate(UUID earlierChangeId) {
        return new ReviewTrigger(ReviewTriggerType.DUPLICATE_CANDIDATE, earlierChangeId.toString());
    }

    // Not a getter, so it is never written into the stored JSON.
    public String describe() {
        return switch (type) {
            case SENSITIVE_PATH -> "Sensitive file " + detail;
            case CHANGED_FILES_UNAVAILABLE -> "Changed files unavailable";
            case CLASSIFIER_FALLBACK -> "AI classification failed";
            case CATEGORY_SUGGESTION_PENDING -> "AI proposed a new category " + detail;
            case CONTEXT_INSUFFICIENT -> detail == null ? "Not enough context" : "Not enough context: " + detail;
            case DUPLICATE_CANDIDATE -> "Possible duplicate of an earlier change";
        };
    }
}
