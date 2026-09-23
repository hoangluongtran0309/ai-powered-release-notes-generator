package com.hoangluongtran0309.releaseflow.translation;

import java.util.UUID;

/**
 * A change's content was translated into a language, or its translation failed for good.
 * Published inside the transaction that recorded the result.
 */
public record TranslationFinished(UUID organizationId, UUID changeId, String targetLanguage) {
}
