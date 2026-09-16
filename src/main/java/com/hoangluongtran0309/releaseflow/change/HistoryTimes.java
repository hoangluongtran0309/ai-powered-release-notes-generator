package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

/** Reading a provider's item must never fail the whole page. */
final class HistoryTimes {

    private HistoryTimes() {
    }

    /** A provider timestamp, or null when it is absent or unreadable. */
    static Instant instant(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.stringValue()).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /** The normalized change, or null when the provider's item cannot be read. */
    static MergedPullRequest readOrNull(Supplier<MergedPullRequest> read) {
        try {
            return read.get();
        } catch (MalformedWebhookPayloadException exception) {
            return null;
        }
    }
}
