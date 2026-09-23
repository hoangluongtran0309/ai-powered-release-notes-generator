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

    static ReviewTrigger sensitiveKeyword(String keyword) {
        return new ReviewTrigger(ReviewTriggerType.SENSITIVE_KEYWORD, keyword);
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

    static ReviewTrigger linkedContextUnavailable(LinkedContextStatus status) {
        return new ReviewTrigger(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE, status.name());
    }

    static ReviewTrigger duplicateCandidate(UUID earlierChangeId) {
        return new ReviewTrigger(ReviewTriggerType.DUPLICATE_CANDIDATE, earlierChangeId.toString());
    }

    /**
     * The bundle key of the sentence a reviewer reads. Not a getter, so it is never
     * written into the stored JSON; the stored trigger keeps only its type and detail.
     */
    public String messageKey() {
        if (type == ReviewTriggerType.CONTEXT_INSUFFICIENT) {
            return detail == null
                    ? "ui.reviewTrigger.CONTEXT_INSUFFICIENT.plain"
                    : "ui.reviewTrigger.CONTEXT_INSUFFICIENT.detailed";
        }
        return "ui.reviewTrigger." + type.name();
    }
}
