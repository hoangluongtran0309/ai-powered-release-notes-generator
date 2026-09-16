package com.hoangluongtran0309.releaseflow.github;

import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubApiClientTest {

    private static final String TOKEN = "github_pat_test-token";
    private static final GitHubStub GITHUB = GitHubStub.start();

    private final GitHubApiClient client = client(Duration.ofSeconds(2));

    @AfterAll
    static void stopStub() {
        GITHUB.close();
    }

    @BeforeEach
    void resetStub() {
        GITHUB.reset();
    }

    @Test
    void listsFilesWithGitHubHeadersAndTheToken() {
        GITHUB.respondWithFiles("src/App.java", "README.md");

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.isCollected()).isTrue();
        assertThat(files.files()).containsExactly(
                new ChangedFile("src/App.java", null, ChangedFileKind.MODIFIED),
                new ChangedFile("README.md", null, ChangedFileKind.MODIFIED)
        );
        GitHubStub.RecordedRequest request = GITHUB.requests().getFirst();
        assertThat(request.path()).isEqualTo("/repos/acme/releaseflow/pulls/42/files");
        assertThat(request.query()).isEqualTo("per_page=100&page=1");
        assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
        assertThat(request.accept()).isEqualTo("application/vnd.github+json");
        assertThat(request.apiVersion()).isEqualTo("2022-11-28");
    }

    @Test
    void mapsRenamesAndPreviousPaths() {
        GITHUB.respondWithRename("src/security/Filter.java", "src/Filter.java");

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.files()).containsExactly(
                new ChangedFile("src/Filter.java", "src/security/Filter.java", ChangedFileKind.RENAMED)
        );
    }

    @Test
    void followsPagesUntilAShortPage() {
        GITHUB.respondWithFileCount(450);

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.files()).hasSize(450);
        assertThat(GITHUB.fileRequests()).extracting(GitHubStub.RecordedRequest::query).containsExactly(
                "per_page=100&page=1",
                "per_page=100&page=2",
                "per_page=100&page=3",
                "per_page=100&page=4",
                "per_page=100&page=5"
        );
    }

    @Test
    void aFullPageIsFollowedByTheNextOne() {
        GITHUB.respondWithFileCount(100);

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.files()).hasSize(100);
        assertThat(GITHUB.fileRequests()).hasSize(2);
    }

    @Test
    void reportsAPossiblyTruncatedListAsUnavailable() {
        GITHUB.respondWithFileCount(500);

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.isCollected()).isFalse();
        assertThat(files.failure()).isEqualTo(ChangedFiles.TOO_MANY_FILES);
        assertThat(files.retryable()).isFalse();
        assertThat(GITHUB.fileRequests()).hasSize(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 429})
    void serverErrorsAndRateLimitsAreRetryable(int status) {
        GITHUB.failFiles(status);

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.GITHUB_UNAVAILABLE);
        assertThat(files.retryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void refusalsAreFinal(int status) {
        GITHUB.failFiles(status);

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.ACCESS_REJECTED);
        assertThat(files.retryable()).isFalse();
    }

    @Test
    void aRateLimitedForbiddenIsRetryable() {
        GITHUB.failFiles(403, Map.of("x-ratelimit-remaining", "0"));

        ChangedFiles files = client.pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.GITHUB_UNAVAILABLE);
        assertThat(files.retryable()).isTrue();
    }

    @Test
    void aTimeoutIsRetryable() {
        GITHUB.respondWithFiles("src/App.java");
        GITHUB.delay(Duration.ofMillis(800));

        ChangedFiles files = client(Duration.ofMillis(200)).pullRequestFiles("acme", "releaseflow", 42, TOKEN);

        assertThat(files.failure()).isEqualTo(ChangedFiles.GITHUB_UNAVAILABLE);
        assertThat(files.retryable()).isTrue();
    }

    @Test
    void checksPullRequestAccess() {
        assertThat(client.checkPullRequestAccess("acme", "releaseflow", TOKEN)).isEqualTo(ProviderAccess.GRANTED);

        GitHubStub.RecordedRequest request = GITHUB.requests().getFirst();
        assertThat(request.path()).isEqualTo("/repos/acme/releaseflow/pulls");
        assertThat(request.query()).isEqualTo("state=closed&per_page=1");
        assertThat(request.authorization()).isEqualTo("Bearer " + TOKEN);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void rejectedAccessIsReported(int status) {
        GITHUB.failAccessCheck(status);

        assertThat(client.checkPullRequestAccess("acme", "releaseflow", TOKEN)).isEqualTo(ProviderAccess.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429})
    void unavailableAccessCheckIsReported(int status) {
        GITHUB.failAccessCheck(status);

        assertThat(client.checkPullRequestAccess("acme", "releaseflow", TOKEN)).isEqualTo(ProviderAccess.UNAVAILABLE);
    }

    @Test
    void refusesToCallGitHubInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.pullRequestFiles("acme", "releaseflow", 42, TOKEN))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> client.checkPullRequestAccess("acme", "releaseflow", TOKEN))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(GITHUB.requests()).isEmpty();
    }

    private static GitHubApiClient client(Duration timeout) {
        return new GitHubApiClient(GITHUB.baseUrl(), timeout, new ObjectMapper(), Clock.systemUTC());
    }
}
