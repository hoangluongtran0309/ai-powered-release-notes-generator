package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final int AUTHOR_LOGIN_MAX_LENGTH = 100;
    private static final int TARGET_BRANCH_MAX_LENGTH = 255;
    private static final int URL_MAX_LENGTH = 2048;

    MergedPullRequest {
        labels = List.copyOf(labels);
    }

    static MergedPullRequest from(JsonNode pullRequest) {
        JsonNode number = pullRequest.path("number");
        if (!number.isInt() || number.intValue() <= 0) {
            throw malformed("pull_request.number");
        }
        return new MergedPullRequest(
                number.intValue(),
                requiredText(pullRequest.path("title"), "pull_request.title", Integer.MAX_VALUE),
                optionalText(pullRequest.path("body")),
                requiredText(pullRequest.path("user").path("login"), "pull_request.user.login", AUTHOR_LOGIN_MAX_LENGTH),
                labels(pullRequest.path("labels")),
                requiredText(pullRequest.path("base").path("ref"), "pull_request.base.ref", TARGET_BRANCH_MAX_LENGTH),
                commitSha(pullRequest.path("merge_commit_sha")),
                instant(pullRequest.path("merged_at"), "pull_request.merged_at"),
                webUrl(pullRequest.path("html_url"))
        );
    }

    private static List<String> labels(JsonNode labels) {
        if (labels.isMissingNode() || labels.isNull()) {
            return List.of();
        }
        if (!labels.isArray()) {
            throw malformed("pull_request.labels");
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode label : labels.values()) {
            names.add(requiredText(label.path("name"), "pull_request.labels[].name", Integer.MAX_VALUE));
        }
        return List.copyOf(names);
    }

    private static String commitSha(JsonNode node) {
        String sha = requiredText(node, "pull_request.merge_commit_sha", Integer.MAX_VALUE);
        if (!COMMIT_SHA.matcher(sha).matches()) {
            throw malformed("pull_request.merge_commit_sha");
        }
        return sha;
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field, Integer.MAX_VALUE));
        } catch (DateTimeParseException exception) {
            throw malformed(field);
        }
    }

    private static String webUrl(JsonNode node) {
        String url = requiredText(node, "pull_request.html_url", URL_MAX_LENGTH);
        try {
            URI uri = new URI(url);
            if (uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                return url;
            }
        } catch (URISyntaxException exception) {
            // Reported below with the same message as a non-HTTP URL.
        }
        throw malformed("pull_request.html_url");
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        if (!node.isString() || node.stringValue().isBlank() || node.stringValue().length() > maxLength) {
            throw malformed(field);
        }
        return node.stringValue();
    }

    private static String optionalText(JsonNode node) {
        if (!node.isString() || node.stringValue().isBlank()) {
            return null;
        }
        return node.stringValue();
    }

    private static MalformedWebhookPayloadException malformed(String field) {
        return new MalformedWebhookPayloadException("The pull request field " + field + " is missing or invalid.");
    }
}
