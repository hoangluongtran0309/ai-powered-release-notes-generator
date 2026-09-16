package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A merged pull or merge request, normalized to what a change records. GitHub's pull
 * requests and GitLab's merge requests both arrive here.
 */
record MergedPullRequest(
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
        return new MergedPullRequest(
                FIELDS.positiveInt(pullRequest.path("number"), "pull_request.number"),
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
