package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Reads the fields a change is normalized from, the same way for every provider. Anything
 * missing, of the wrong shape, or too long for its column is refused rather than trimmed.
 *
 * @param subject how to name the thing being read in an error message
 */
record PayloadFields(String subject) {

    static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    static final int AUTHOR_LOGIN_MAX_LENGTH = 100;
    static final int TARGET_BRANCH_MAX_LENGTH = 255;
    static final int URL_MAX_LENGTH = 2048;

    /** Both providers number a pull or merge request with a positive JSON integer. */
    int positiveInt(JsonNode node, String field) {
        if (!node.isInt() || node.intValue() <= 0) {
            throw malformed(field);
        }
        return node.intValue();
    }

    String requiredText(JsonNode node, String field, int maxLength) {
        if (!node.isString() || node.stringValue().isBlank() || node.stringValue().length() > maxLength) {
            throw malformed(field);
        }
        return node.stringValue();
    }

    String optionalText(JsonNode node) {
        if (!node.isString() || node.stringValue().isBlank()) {
            return null;
        }
        return node.stringValue();
    }

    String commitSha(JsonNode node, String field) {
        String sha = requiredText(node, field, Integer.MAX_VALUE);
        if (!COMMIT_SHA.matcher(sha).matches()) {
            throw malformed(field);
        }
        return sha;
    }

    Instant instant(JsonNode node, String field) {
        Instant instant = optionalInstant(node);
        if (instant == null) {
            throw malformed(field);
        }
        return instant;
    }

    Instant optionalInstant(JsonNode node) {
        if (!node.isString() || node.stringValue().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.stringValue()).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    String webUrl(JsonNode node, String field) {
        String url = requiredText(node, field, URL_MAX_LENGTH);
        try {
            URI uri = new URI(url);
            if (uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                return url;
            }
        } catch (URISyntaxException exception) {
            // Reported below with the same message as a non-HTTP URL.
        }
        throw malformed(field);
    }

    MalformedWebhookPayloadException malformed(String field) {
        return new MalformedWebhookPayloadException("The " + subject + " field " + field + " is missing or invalid.");
    }
}
