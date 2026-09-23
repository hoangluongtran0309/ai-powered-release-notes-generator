package com.hoangluongtran0309.releaseflow.category;

import java.time.Instant;
import java.util.UUID;

public record CategorySuggestionView(
        UUID id,
        UUID projectId,
        UUID changeId,
        String proposedCode,
        String proposedName,
        CategoryGroup proposedGroup,
        String rationale,
        CategorySuggestionStatus status,
        String resolvedCode,
        String deciderName,
        Instant createdAt,
        Instant decidedAt
) {

    public boolean pending() {
        return status == CategorySuggestionStatus.PENDING_REVIEW;
    }
}
