package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * Reads a Linear issue that has just been completed into the same
 * {@link MergedPullRequest} a pull or merge request becomes. An issue has no merge commit
 * and no target branch, and its number is the one a person sees in {@code ENG-123}, while
 * the source names it by a UUID.
 */
final class CompletedIssue {

    private static final PayloadFields FIELDS = new PayloadFields("issue");
    // Any real epoch-millisecond value is larger than this; any epoch-second value is not.
    private static final long MILLISECOND_THRESHOLD = 10_000_000_000L;

    private CompletedIssue() {
    }

    static MergedPullRequest fromWebhook(JsonNode payload) {
        JsonNode data = payload.path("data");
        return new MergedPullRequest(
                FIELDS.requiredText(data.path("id"), "data.id", 100),
                FIELDS.positiveInt(data.path("number"), "data.number"),
                FIELDS.requiredText(data.path("title"), "data.title", Integer.MAX_VALUE),
                FIELDS.optionalText(data.path("description")),
                // Linear does not always name a creator, and inventing one would be a lie.
                creator(data),
                labels(data),
                null,
                null,
                completedAt(payload.path("createdAt")),
                FIELDS.webUrl(data.path("url"), "data.url")
        );
    }

    /** The team the issue belongs to, which must be the team the source is connected to. */
    static String teamId(JsonNode payload) {
        JsonNode data = payload.path("data");
        JsonNode nested = data.path("team").path("id");
        return nested.isString() ? nested.stringValue() : data.path("teamId").asString("");
    }

    /**
     * Whether this delivery is an issue that has just moved into a completed state. An
     * issue that was already completed, or that changed something other than its state,
     * is not a change.
     */
    static boolean isCompletion(JsonNode payload) {
        if (!"Issue".equals(payload.path("type").asString(""))
                || !"update".equals(payload.path("action").asString(""))) {
            return false;
        }
        JsonNode previous = payload.path("updatedFrom");
        boolean stateChanged = previous.has("stateId") || previous.has("stateType") || previous.has("state");
        return stateChanged
                && "completed".equalsIgnoreCase(stateType(payload.path("data")))
                && !"completed".equalsIgnoreCase(stateType(previous));
    }

    // Linear writes the state either nested or flattened, depending on the payload version.
    private static String stateType(JsonNode node) {
        JsonNode nested = node.path("state").path("type");
        return nested.isString() ? nested.stringValue() : node.path("stateType").asString("");
    }

    private static String creator(JsonNode data) {
        String name = data.path("creator").path("name").asString("");
        return name.isBlank() ? null : name;
    }

    private static List<String> labels(JsonNode data) {
        JsonNode labels = data.path("labels");
        if (!labels.isArray()) {
            return List.of();
        }
        return labels.valueStream()
                .map(label -> label.isString() ? label.stringValue() : label.path("name").asString(""))
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    // Linear stamps the delivery, not the issue, with the moment the state changed.
    private static Instant completedAt(JsonNode createdAt) {
        if (createdAt.isNumber()) {
            long raw = createdAt.longValue();
            return raw > MILLISECOND_THRESHOLD ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        Instant instant = FIELDS.optionalInstant(createdAt);
        if (instant == null) {
            throw FIELDS.malformed("createdAt");
        }
        return instant;
    }
}
