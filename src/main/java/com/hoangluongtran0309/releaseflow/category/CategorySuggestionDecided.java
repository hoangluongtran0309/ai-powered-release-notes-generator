package com.hoangluongtran0309.releaseflow.category;

import java.util.UUID;

/**
 * Published inside the decision's transaction. {@code category} is the category the
 * change should now carry, or null when the suggestion was rejected.
 */
public record CategorySuggestionDecided(UUID organizationId, UUID projectId, UUID changeId, CategoryRef category) {
}
