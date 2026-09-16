package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A change as the pipeline records it: a merged pull or merge request, or a completed
 * issue. {@code externalId} is how the source names it, and is what makes the change
 * idempotent; {@code number} is what a person sees. A source without a merge commit or a
 * target branch leaves those null.
 */
record MergedPullRequest(
        String externalId,
        int number,
        String title,
        String description,
        String authorLogin,
        List<String> labels,
        String targetBranch,
        String mergeCommitSha,
        Instant mergedAt,
        String url
) {

    private static final PayloadFields FIELDS = new PayloadFields("pull request");

    MergedPullRequest {
        labels = List.copyOf(labels);
    }

    static MergedPullRequest from(JsonNode pullRequest) {
        int number = FIELDS.positiveInt(pullRequest.path("number"), "pull_request.number");
        return new MergedPullRequest(
                Integer.toString(number),
                number,
                FIELDS.requiredText(pullRequest.path("title"), "pull_request.title", Integer.MAX_VALUE),
                FIELDS.optionalText(pullRequest.path("body")),
                FIELDS.requiredText(
                        pullRequest.path("user").path("login"),
                        "pull_request.user.login",
                        PayloadFields.AUTHOR_LOGIN_MAX_LENGTH
                ),
                labels(pullRequest.path("labels")),
                FIELDS.requiredText(
                        pullRequest.path("base").path("ref"),
                        "pull_request.base.ref",
                        PayloadFields.TARGET_BRANCH_MAX_LENGTH
                ),
                FIELDS.commitSha(pullRequest.path("merge_commit_sha"), "pull_request.merge_commit_sha"),
                FIELDS.instant(pullRequest.path("merged_at"), "pull_request.merged_at"),
                FIELDS.webUrl(pullRequest.path("html_url"), "pull_request.html_url")
        );
    }

    private static List<String> labels(JsonNode labels) {
        if (labels.isMissingNode() || labels.isNull()) {
            return List.of();
        }
        if (!labels.isArray()) {
            throw FIELDS.malformed("pull_request.labels");
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode label : labels.values()) {
            names.add(FIELDS.requiredText(label.path("name"), "pull_request.labels[].name", Integer.MAX_VALUE));
        }
        return List.copyOf(names);
    }
}
