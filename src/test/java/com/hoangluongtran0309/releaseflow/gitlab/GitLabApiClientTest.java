package com.hoangluongtran0309.releaseflow.gitlab;

import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
import com.hoangluongtran0309.releaseflow.support.GitLabStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLabApiClientTest {

    private static final String TOKEN = "glpat-test-token";
    private static final String PROJECT = "acme/group/app";
    private static final Instant NOW = Instant.parse("2026-09-16T09:00:00Z");
    private static final GitLabStub GITLAB = GitLabStub.start();

    private final GitLabApiClient client = client(Duration.ofSeconds(2));

    @AfterAll
    static void stopStub() {
        GITLAB.close();
    }

    @BeforeEach
    void resetStub() {
        GITLAB.reset();
    }

    @Test
    void listsFilesWithThePrivateTokenAndAnEncodedProjectPath() {
        GITLAB.respondWithFiles("src/App.java", "README.md");

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.isCollected()).isTrue();
        assertThat(files.files()).containsExactly(
                new ChangedFile("src/App.java", null, ChangedFileKind.MODIFIED),
                new ChangedFile("README.md", null, ChangedFileKind.MODIFIED)
        );
        GitLabStub.RecordedRequest request = GITLAB.requests().getFirst();
        // The path is one template variable, so its slashes are encoded exactly once.
        assertThat(request.path()).isEqualTo("/api/v4/projects/acme%2Fgroup%2Fapp/merge_requests/42/diffs");
        assertThat(request.query()).isEqualTo("per_page=100&page=1");
        assertThat(request.privateToken()).isEqualTo(TOKEN);
        assertThat(request.accept()).isEqualTo("application/json");
    }

    @Test
    void readsEveryKindOfDiffEntry() {
        GITLAB.respondWithDiffs(List.of(
                GitLabStub.diff("", "src/New.java", Map.of("new_file", true)),
                GitLabStub.diff("src/Gone.java", "", Map.of("deleted_file", true)),
                GitLabStub.diff("src/Old.java", "src/New2.java", Map.of("renamed_file", true)),
                GitLabStub.diff("src/Same.java", "src/Same.java", Map.of())
        ));

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.files()).containsExactly(
                new ChangedFile("src/New.java", null, ChangedFileKind.ADDED),
                new ChangedFile("src/Gone.java", null, ChangedFileKind.REMOVED),
                new ChangedFile("src/New2.java", "src/Old.java", ChangedFileKind.RENAMED),
                new ChangedFile("src/Same.java", null, ChangedFileKind.MODIFIED)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"collapsed", "too_large", "truncated"})
    void aWithheldDiffMakesTheWholeListUnavailable(String flag) {
        GITLAB.respondWithDiffs(List.of(
                GitLabStub.diff("src/App.java", "src/App.java", Map.of()),
                GitLabStub.diff("src/Big.java", "src/Big.java", Map.of(flag, true))
        ));

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.isCollected()).isFalse();
        assertThat(files.failure()).isEqualTo(ChangedFiles.DIFF_UNAVAILABLE);
        assertThat(files.retryable()).isFalse();
    }

    @Test
    void followsPagesUntilAShortPage() {
        GITLAB.respondWithFileCount(150);

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.files()).hasSize(150);
        assertThat(GITLAB.diffRequests()).hasSize(2);
    }

    @Test
    void aListThatMayBeTruncatedIsUnavailable() {
        GITLAB.respondWithFileCount(GitLabApiClient.MAX_DIFF_PAGES * GitLabApiClient.PAGE_SIZE + 1);

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.TOO_MANY_FILES);
        assertThat(files.retryable()).isFalse();
        assertThat(GITLAB.diffRequests()).hasSize(GitLabApiClient.MAX_DIFF_PAGES);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void aRefusedDiffRequestIsFinal(int status) {
        GITLAB.failDiffs(status);

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.ACCESS_REJECTED);
        assertThat(files.retryable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void aTransientDiffFailureIsRetryable(int status) {
        GITLAB.failDiffs(status);

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.GITLAB_UNAVAILABLE);
        assertThat(files.retryable()).isTrue();
    }

    @Test
    void aTimeoutIsRetryable() {
        GITLAB.respondWithFiles("src/App.java");
        GITLAB.delay(Duration.ofSeconds(1));

        ChangedFiles files = client(Duration.ofMillis(200)).mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.GITLAB_UNAVAILABLE);
        assertThat(files.retryable()).isTrue();
    }

    @Test
    void aRedirectIsNotFollowed() {
        GITLAB.redirectTo("https://gitlab.example.invalid/api/v4/projects/1/merge_requests/42/diffs");

        ChangedFiles files = client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN);

        assertThat(files.isCollected()).isFalse();
        assertThat(GITLAB.requests()).hasSize(1);
    }

    @Test
    void asksForTheMergedMergeRequestsUpdatedAfterTheWindowStart() {
        Instant windowStart = NOW.minus(Duration.ofDays(90));
        GITLAB.respondWithMergeRequests(List.of(GitLabStub.mergedMergeRequest(7, NOW, NOW)));

        ProviderListing listing = client.mergedMergeRequests(GITLAB.baseUrl(), PROJECT, 1, windowStart, TOKEN);

        assertThat(listing.status()).isEqualTo(ProviderListing.Status.LISTED);
        assertThat(listing.items()).hasSize(1);
        assertThat(listing.lastPage()).isTrue();
        GitLabStub.RecordedRequest request = GITLAB.listRequests().getFirst();
        assertThat(request.path()).isEqualTo("/api/v4/projects/acme%2Fgroup%2Fapp/merge_requests");
        assertThat(request.query()).contains("state=merged", "order_by=updated_at", "sort=asc", "per_page=100", "page=1");
        assertThat(request.query()).contains("updated_after=" + windowStart.toString().replace(":", "%3A"));
    }

    @Test
    void aRateLimitedListingReportsHowLongToWait() {
        GITLAB.failMergeRequestList(429, Map.of("Retry-After", "120"), 1);

        ProviderListing listing = client.mergedMergeRequests(GITLAB.baseUrl(), PROJECT, 1, NOW, TOKEN);

        assertThat(listing.status()).isEqualTo(ProviderListing.Status.RATE_LIMITED);
        assertThat(listing.retryAfter()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void anExhaustedRateLimitWindowReportsItsResetTime() {
        GITLAB.failMergeRequestList(403, Map.of("RateLimit-Reset", Long.toString(NOW.getEpochSecond() + 90)), 1);

        ProviderListing listing = client.mergedMergeRequests(GITLAB.baseUrl(), PROJECT, 1, NOW, TOKEN);

        assertThat(listing.status()).isEqualTo(ProviderListing.Status.RATE_LIMITED);
        assertThat(listing.retryAfter()).isEqualTo(Duration.ofSeconds(90));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void aRefusedListingIsFinal(int status) {
        GITLAB.failMergeRequestList(status, Map.of(), 1);

        ProviderListing listing = client.mergedMergeRequests(GITLAB.baseUrl(), PROJECT, 1, NOW, TOKEN);

        assertThat(listing.status()).isEqualTo(ProviderListing.Status.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void anUnavailableListingIsReported(int status) {
        GITLAB.failMergeRequestList(status, Map.of(), 1);

        ProviderListing listing = client.mergedMergeRequests(GITLAB.baseUrl(), PROJECT, 1, NOW, TOKEN);

        assertThat(listing.status()).isEqualTo(ProviderListing.Status.UNAVAILABLE);
    }

    @Test
    void checksProjectAccess() {
        assertThat(client.checkProjectAccess(GITLAB.baseUrl(), PROJECT, TOKEN)).isEqualTo(ProviderAccess.GRANTED);

        GitLabStub.RecordedRequest request = GITLAB.requests().getFirst();
        assertThat(request.path()).isEqualTo("/api/v4/projects/acme%2Fgroup%2Fapp");
        assertThat(request.privateToken()).isEqualTo(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void rejectedAccessIsReported(int status) {
        GITLAB.failProjectCheck(status);

        assertThat(client.checkProjectAccess(GITLAB.baseUrl(), PROJECT, TOKEN)).isEqualTo(ProviderAccess.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429})
    void unavailableAccessCheckIsReported(int status) {
        GITLAB.failProjectCheck(status);

        assertThat(client.checkProjectAccess(GITLAB.baseUrl(), PROJECT, TOKEN)).isEqualTo(ProviderAccess.UNAVAILABLE);
    }

    @Test
    void refusesAnInstanceTheDeploymentDoesNotAllow() {
        assertThatThrownBy(() -> client.checkProjectAccess("https://gitlab.internal", PROJECT, TOKEN))
                .isInstanceOf(GitLabHostNotAllowedException.class);
        assertThat(GITLAB.requests()).isEmpty();
    }

    @Test
    void refusesToCallGitLabInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.mergeRequestFiles(GITLAB.baseUrl(), PROJECT, 42, TOKEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be called inside a database transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private GitLabApiClient client(Duration timeout) {
        return new GitLabApiClient(
                new GitLabBaseUrl("gitlab.com," + GITLAB.allowedOrigin()),
                timeout,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
