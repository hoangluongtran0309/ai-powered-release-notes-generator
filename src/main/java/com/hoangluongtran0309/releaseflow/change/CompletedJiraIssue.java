package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Jira issue that is done into the same {@link MergedPullRequest} a pull request
 * becomes. An issue has no merge commit and no branch; the number a person sees is the one
 * in its key, while Jira names it by an internal ID.
 */
final class CompletedJiraIssue {

    private static final PayloadFields FIELDS = new PayloadFields("issue");
    private static final Pattern KEY = Pattern.compile("^[A-Z][A-Z0-9_]*-([1-9][0-9]*)$");
    // Jira Cloud still writes the classic offset without a colon.
    private static final DateTimeFormatter CLASSIC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private CompletedJiraIssue() {
    }

    static MergedPullRequest from(JsonNode issue, String siteUrl, String description) {
        String key = FIELDS.requiredText(issue.path("key"), "key", 100);
        JsonNode fields = issue.path("fields");
        return new MergedPullRequest(
                FIELDS.requiredText(issue.path("id"), "id", 100),
                number(key),
                key + ": " + FIELDS.requiredText(fields.path("summary"), "fields.summary", Integer.MAX_VALUE),
                description == null || description.isBlank() ? null : description,
                reporter(fields),
                List.of(),
                null,
                null,
                completedAt(fields),
                // Built by ReleaseFlow from a site it already validated, not taken from the payload.
                siteUrl + "/browse/" + key
        );
    }

    /** When Jira says the issue was finished, which is what decides the window it falls in. */
    static Instant completedAt(JsonNode fields) {
        Instant resolved = instant(fields.path("resolutiondate"));
        if (resolved != null) {
            return resolved;
        }
        Instant updated = instant(fields.path("updated"));
        if (updated == null) {
            throw FIELDS.malformed("fields.resolutiondate");
        }
        return updated;
    }

    private static Instant instant(JsonNode node) {
        if (!node.isString() || node.stringValue().isBlank()) {
            return null;
        }
        String value = node.stringValue();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException isoFailed) {
            try {
                return OffsetDateTime.parse(value, CLASSIC_TIME).toInstant();
            } catch (DateTimeParseException classicFailed) {
                return null;
            }
        }
    }

    private static int number(String key) {
        Matcher matcher = KEY.matcher(key);
        if (!matcher.matches()) {
            throw FIELDS.malformed("key");
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String reporter(JsonNode fields) {
        String name = fields.path("reporter").path("displayName").asString("");
        return name.isBlank() ? null : name;
    }
}
