package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads a merged GitLab merge request into the same {@link MergedPullRequest} a GitHub
 * pull request becomes, from either a webhook delivery or the REST list of merged merge
 * requests. The two shapes name several fields differently, so each has its own reader.
 */
final class MergedMergeRequest {

    private static final PayloadFields FIELDS = new PayloadFields("merge request");
    // Older GitLab webhooks write "2026-09-10 09:14:22 UTC" where the API writes ISO-8601.
    private static final DateTimeFormatter WEBHOOK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz");

    private MergedMergeRequest() {
    }

    /** The {@code merge_request} event's own fields; its labels sit beside the attributes. */
    static MergedPullRequest fromWebhook(JsonNode payload) {
        JsonNode attributes = payload.path("object_attributes");
        JsonNode labels = payload.path("labels").isArray() ? payload.path("labels") : attributes.path("labels");
        int iid = FIELDS.positiveInt(attributes.path("iid"), "object_attributes.iid");
        return new MergedPullRequest(
                Integer.toString(iid),
                iid,
                FIELDS.requiredText(attributes.path("title"), "object_attributes.title", Integer.MAX_VALUE),
                FIELDS.optionalText(attributes.path("description")),
                // The merge event names whoever merged it; GitLab does not name the author here.
                FIELDS.requiredText(
                        payload.path("user").path("username"),
                        "user.username",
                        PayloadFields.AUTHOR_LOGIN_MAX_LENGTH
                ),
                titleLabels(labels),
                FIELDS.requiredText(
                        attributes.path("target_branch"),
                        "object_attributes.target_branch",
                        PayloadFields.TARGET_BRANCH_MAX_LENGTH
                ),
                mergeCommitSha(attributes, attributes.path("last_commit").path("id"), "object_attributes"),
                mergedAt(attributes, webhookInstant(attributes.path("updated_at")), "object_attributes"),
                FIELDS.webUrl(attributes.path("url"), "object_attributes.url")
        );
    }

    /** One merge request from {@code GET /projects/{id}/merge_requests}. */
    static MergedPullRequest fromListItem(JsonNode mergeRequest) {
        int iid = FIELDS.positiveInt(mergeRequest.path("iid"), "iid");
        return new MergedPullRequest(
                Integer.toString(iid),
                iid,
                FIELDS.requiredText(mergeRequest.path("title"), "title", Integer.MAX_VALUE),
                FIELDS.optionalText(mergeRequest.path("description")),
                FIELDS.requiredText(
                        mergeRequest.path("author").path("username"),
                        "author.username",
                        PayloadFields.AUTHOR_LOGIN_MAX_LENGTH
                ),
                stringLabels(mergeRequest.path("labels")),
                FIELDS.requiredText(
                        mergeRequest.path("target_branch"),
                        "target_branch",
                        PayloadFields.TARGET_BRANCH_MAX_LENGTH
                ),
                mergeCommitSha(mergeRequest, mergeRequest.path("sha"), ""),
                mergedAt(mergeRequest, FIELDS.optionalInstant(mergeRequest.path("updated_at")), ""),
                FIELDS.webUrl(mergeRequest.path("web_url"), "web_url")
        );
    }

    /**
     * A squashed or fast-forwarded merge request has no merge commit, so the commit the
     * change is identified by falls back to the squash commit and then to the head commit.
     */
    private static String mergeCommitSha(JsonNode node, JsonNode headCommit, String prefix) {
        for (JsonNode candidate : List.of(node.path("merge_commit_sha"), node.path("squash_commit_sha"), headCommit)) {
            if (candidate.isString() && PayloadFields.COMMIT_SHA.matcher(candidate.stringValue()).matches()) {
                return candidate.stringValue();
            }
        }
        throw FIELDS.malformed(field(prefix, "merge_commit_sha"));
    }

    /** A webhook delivery carries no merge time, so the last update stands in for it. */
    private static Instant mergedAt(JsonNode node, Instant fallback, String prefix) {
        Instant mergedAt = FIELDS.optionalInstant(node.path("merged_at"));
        if (mergedAt != null) {
            return mergedAt;
        }
        if (fallback != null) {
            return fallback;
        }
        throw FIELDS.malformed(field(prefix, "merged_at"));
    }

    // Webhook labels are objects with a title.
    private static List<String> titleLabels(JsonNode labels) {
        return labels(labels, label -> FIELDS.requiredText(label.path("title"), "labels[].title", Integer.MAX_VALUE));
    }

    // The REST list writes labels as plain strings.
    private static List<String> stringLabels(JsonNode labels) {
        return labels(labels, label -> FIELDS.requiredText(label, "labels[]", Integer.MAX_VALUE));
    }

    private static List<String> labels(JsonNode labels, Function<JsonNode, String> name) {
        if (labels.isMissingNode() || labels.isNull()) {
            return List.of();
        }
        if (!labels.isArray()) {
            throw FIELDS.malformed("labels");
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode label : labels.values()) {
            names.add(name.apply(label));
        }
        return List.copyOf(names);
    }

    private static Instant webhookInstant(JsonNode node) {
        Instant instant = FIELDS.optionalInstant(node);
        if (instant != null || !node.isString()) {
            return instant;
        }
        try {
            return ZonedDateTime.parse(node.stringValue(), WEBHOOK_TIME).toInstant();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String field(String prefix, String name) {
        return prefix.isEmpty() ? name : prefix + "." + name;
    }
}
