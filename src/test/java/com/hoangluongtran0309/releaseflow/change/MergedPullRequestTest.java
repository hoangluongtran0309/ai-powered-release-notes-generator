package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MergedPullRequestTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String VALID = """
            {"number":7,"title":"Fix login redirect","body":"Details","user":{"login":"dependabot[bot]"},
             "labels":[{"name":"bug"},{"name":"security"},{"name":"bug"}],"base":{"ref":"release/1.2"},
             "merge_commit_sha":"%s","merged_at":"2026-09-10T09:14:22Z",
             "html_url":"https://github.com/acme/releaseflow/pull/7"}
            """.formatted("a".repeat(40));

    @Test
    void normalizesMergedPullRequestFields() {
        MergedPullRequest pullRequest = MergedPullRequest.from(json(VALID));

        assertThat(pullRequest.number()).isEqualTo(7);
        assertThat(pullRequest.title()).isEqualTo("Fix login redirect");
        assertThat(pullRequest.description()).isEqualTo("Details");
        assertThat(pullRequest.authorLogin()).isEqualTo("dependabot[bot]");
        assertThat(pullRequest.labels()).containsExactly("bug", "security");
        assertThat(pullRequest.targetBranch()).isEqualTo("release/1.2");
        assertThat(pullRequest.mergeCommitSha()).isEqualTo("a".repeat(40));
        assertThat(pullRequest.mergedAt()).isEqualTo(Instant.parse("2026-09-10T09:14:22Z"));
        assertThat(pullRequest.url()).isEqualTo("https://github.com/acme/releaseflow/pull/7");
    }

    @Test
    void treatsEmptyBodyAndMissingLabelsAsAbsent() {
        MergedPullRequest pullRequest = MergedPullRequest.from(json(VALID
                .replace("\"body\":\"Details\"", "\"body\":null")
                .replace("\"labels\":[{\"name\":\"bug\"},{\"name\":\"security\"},{\"name\":\"bug\"}],", "")));

        assertThat(pullRequest.description()).isNull();
        assertThat(pullRequest.labels()).isEmpty();
    }

    @Test
    void acceptsSha256Commits() {
        String sha = "b".repeat(64);

        assertThat(MergedPullRequest.from(json(VALID.replace("a".repeat(40), sha))).mergeCommitSha())
                .isEqualTo(sha);
    }

    @Test
    void rejectsMissingOrInvalidRequiredFields() {
        assertMalformed(VALID.replace("\"number\":7", "\"number\":0"), "pull_request.number");
        assertMalformed(VALID.replace("\"number\":7", "\"number\":\"7\""), "pull_request.number");
        assertMalformed(VALID.replace("\"Fix login redirect\"", "\"  \""), "pull_request.title");
        assertMalformed(VALID.replace("{\"login\":\"dependabot[bot]\"}", "{}"), "pull_request.user.login");
        assertMalformed(VALID.replace("{\"ref\":\"release/1.2\"}", "null"), "pull_request.base.ref");
        assertMalformed(VALID.replace("a".repeat(40), "A".repeat(40)), "pull_request.merge_commit_sha");
        assertMalformed(VALID.replace("a".repeat(40), "a".repeat(39)), "pull_request.merge_commit_sha");
        assertMalformed(VALID.replace("2026-09-10T09:14:22Z", "yesterday"), "pull_request.merged_at");
        assertMalformed(VALID.replace("https://github.com", "javascript:alert(1)//github.com"), "pull_request.html_url");
        assertMalformed(VALID.replace("[{\"name\":\"bug\"}", "[{\"id\":1}"), "pull_request.labels[].name");
    }

    private static void assertMalformed(String payload, String field) {
        assertThatThrownBy(() -> MergedPullRequest.from(json(payload)))
                .isInstanceOf(MalformedWebhookPayloadException.class)
                .hasMessageContaining(field);
    }

    private static JsonNode json(String value) {
        return OBJECT_MAPPER.readTree(value);
    }
}
