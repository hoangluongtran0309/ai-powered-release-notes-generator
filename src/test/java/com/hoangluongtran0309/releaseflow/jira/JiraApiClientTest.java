package com.hoangluongtran0309.releaseflow.jira;

import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraApiClientTest {

    private static final String EMAIL = "release-bot@acme.test";
    private static final String TOKEN = "atlassian-test-token";
    private static final JiraStub JIRA = JiraStub.start();

    private final JiraApiClient client = new JiraApiClient(
            new JiraSiteUrl(), Duration.ofSeconds(2), 40, JIRA.baseUrl(), new ObjectMapper());

    @AfterAll
    static void stopStub() {
        JIRA.close();
    }

    @BeforeEach
    void resetStub() {
        JIRA.reset();
    }

    @Test
    void checksTheProjectWithBasicCredentials() {
        assertThat(client.checkProjectAccess(JiraStub.SITE, "APP", EMAIL, TOKEN)).isEqualTo(ProviderAccess.GRANTED);

        JiraStub.RecordedRequest request = JIRA.requests().getFirst();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/rest/api/3/project/APP");
        assertThat(request.authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((EMAIL + ":" + TOKEN).getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void aRefusedProjectRejectsTheToken(int status) {
        JIRA.respondToProjectWith(status);

        assertThat(client.checkProjectAccess(JiraStub.SITE, "APP", EMAIL, TOKEN)).isEqualTo(ProviderAccess.REJECTED);
    }

    @Test
    void aServerErrorOrARedirectConfirmsNothing() {
        JIRA.respondToProjectWith(500);
        assertThat(client.checkProjectAccess(JiraStub.SITE, "APP", EMAIL, TOKEN)).isEqualTo(ProviderAccess.UNAVAILABLE);

        JIRA.respondToProjectWith(200);
        JIRA.redirectTo("https://evil.example.invalid/rest/api/3/project/APP");
        assertThat(client.checkProjectAccess(JiraStub.SITE, "APP", EMAIL, TOKEN)).isEqualTo(ProviderAccess.UNAVAILABLE);
        assertThat(JIRA.requests()).hasSize(2);
    }

    @Test
    void searchesDoneIssuesInUpdateOrderAndPagesWithTheToken() {
        Instant updated = Instant.parse("2026-09-10T08:00:00Z");
        JIRA.respondWithSearchPages(
                List.of(JiraStub.doneIssue("1", "APP-1", "First", "Mai", updated, updated)),
                List.of(JiraStub.doneIssue("2", "APP-2", "Second", "Mai", updated, updated))
        );

        ProviderListing first = client.completedIssues(
                JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.parse("2026-09-10T07:05:59.999Z"), "");
        assertThat(first.status()).isEqualTo(ProviderListing.Status.LISTED);
        assertThat(first.items()).extracting(issue -> issue.path("key").asString()).containsExactly("APP-1");
        assertThat(first.nextPageToken()).isEqualTo("page-1");
        assertThat(first.lastPage()).isFalse();

        ProviderListing second = client.completedIssues(
                JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.parse("2026-09-10T07:05:00Z"), first.nextPageToken());
        assertThat(second.items()).extracting(issue -> issue.path("key").asString()).containsExactly("APP-2");
        assertThat(second.lastPage()).isTrue();

        List<JiraStub.RecordedRequest> searches = JIRA.searchRequests();
        // Minutes in UTC: Jira's JQL has no finer grain.
        assertThat(searches.getFirst().query())
                .containsEntry("jql", "project = \"APP\" AND statusCategory = Done"
                        + " AND updated >= \"2026-09-10 07:05\" ORDER BY updated ASC")
                .containsEntry("maxResults", "100")
                .containsEntry("fields", "summary,description,reporter,updated,resolutiondate")
                .doesNotContainKey("nextPageToken");
        assertThat(searches.get(1).query()).containsEntry("nextPageToken", "page-1");
    }

    @Test
    void aProjectKeyCannotCloseTheJqlString() {
        client.completedIssues(JiraStub.SITE, "APP\" OR project = \"OPS", EMAIL, TOKEN, Instant.EPOCH, "");

        assertThat(JIRA.searchRequests().getFirst().query().get("jql"))
                .startsWith("project = \"APP OR project = OPS\" AND");
    }

    @Test
    void reportsWhyASearchFailed() {
        JIRA.failSearch(429, Map.of("Retry-After", "30"), 1);
        ProviderListing limited = client.completedIssues(JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.EPOCH, "");
        assertThat(limited.status()).isEqualTo(ProviderListing.Status.RATE_LIMITED);
        assertThat(limited.retryAfter()).isEqualTo(Duration.ofSeconds(30));

        JIRA.failSearch(401, Map.of(), 1);
        assertThat(client.completedIssues(JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.EPOCH, "").status())
                .isEqualTo(ProviderListing.Status.REJECTED);

        JIRA.failSearch(502, Map.of(), 1);
        assertThat(client.completedIssues(JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.EPOCH, "").status())
                .isEqualTo(ProviderListing.Status.UNAVAILABLE);

        JIRA.redirectTo("https://evil.example.invalid/");
        assertThat(client.completedIssues(JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.EPOCH, "").status())
                .isEqualTo(ProviderListing.Status.INVALID_RESPONSE);
    }

    @Test
    void readsAnIssueAndLinksItOnTheSiteNotTheAddressItWasFetchedFrom() {
        JIRA.respondWithIssue("APP-7", "Publish notes", "Story", "Done",
                "A description far longer than the forty characters this client keeps.");

        assertThat(client.issue(JiraStub.SITE + "/", "APP-7", EMAIL, TOKEN)).contains(new LinkedIssue(
                "APP-7",
                "Publish notes",
                "A description far longer than the forty",
                "Story",
                "Done",
                "https://acme.atlassian.net/browse/APP-7"
        ));
        assertThat(JIRA.issueRequests().getFirst().query())
                .containsEntry("fields", "summary,description,issuetype,status");
    }

    @Test
    void anIssueJiraWillNotShowIsMissing() {
        JIRA.failIssue("APP-8");

        assertThat(client.issue(JiraStub.SITE, "APP-8", EMAIL, TOKEN)).isEmpty();
        assertThat(client.issue(JiraStub.SITE, "APP-9", EMAIL, TOKEN)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://acme.atlassian.net",
            "https://acme.atlassian.net:8443",
            "https://user@acme.atlassian.net",
            "https://acme.atlassian.net?x=1",
            "https://acme.atlassian.net#top",
            "https://acme.atlassian.net.evil.test",
            "https://atlassian.network",
            "https://127.0.0.1",
            "acme.atlassian.net",
            ""
    })
    void refusesASiteOutsideAtlassianCloudBeforeCallingAnything(String site) {
        assertThatThrownBy(() -> client.checkProjectAccess(site, "APP", EMAIL, TOKEN))
                .isInstanceOf(InvalidJiraSiteException.class);
        assertThatThrownBy(() -> client.issue(site, "APP-1", EMAIL, TOKEN))
                .isInstanceOf(InvalidJiraSiteException.class);
        assertThat(JIRA.requests()).isEmpty();
    }

    @Test
    void acceptsTheCloudDomainAndDropsTrailingSlashes() {
        JiraSiteUrl sites = new JiraSiteUrl();

        assertThat(sites.validated(" https://Acme.atlassian.net// ")).isEqualTo("https://Acme.atlassian.net");
        assertThat(sites.validated("https://atlassian.net")).isEqualTo("https://atlassian.net");
    }

    @Test
    void refusesToCallJiraInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.checkProjectAccess(JiraStub.SITE, "APP", EMAIL, TOKEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be called inside a database transaction");
            assertThatThrownBy(() -> client.completedIssues(JiraStub.SITE, "APP", EMAIL, TOKEN, Instant.EPOCH, ""))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> client.issue(JiraStub.SITE, "APP-1", EMAIL, TOKEN))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(JIRA.requests()).isEmpty();
    }
}
