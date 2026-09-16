package com.hoangluongtran0309.releaseflow.linear;

import com.hoangluongtran0309.releaseflow.support.LinearStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinearApiClientTest {

    private static final String TOKEN = "lin_api_test-token";
    private static final String TEAM = "11111111-1111-4111-8111-111111111111";
    private static final String WORKSPACE = "22222222-2222-4222-8222-222222222222";
    private static final String ISSUE = "33333333-3333-4333-8333-333333333333";
    private static final LinearStub LINEAR = LinearStub.start();

    private final LinearApiClient client = client(Duration.ofSeconds(2));

    @AfterAll
    static void stopStub() {
        LINEAR.close();
    }

    @BeforeEach
    void resetStub() {
        LINEAR.reset();
    }

    @Test
    void namesTheWorkspaceOfATeamTheTokenCanRead() {
        LINEAR.respondWithTeam(TEAM, WORKSPACE);

        assertThat(client.workspaceOf(TEAM, TOKEN)).contains(WORKSPACE);

        LinearStub.RecordedRequest request = LINEAR.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/graphql");
        // Linear takes the token bare; a Bearer prefix would be rejected.
        assertThat(request.authorization()).isEqualTo(TOKEN);
        assertThat(request.query()).isEqualTo("query($id:String!){team(id:$id){id organization{id}}}");
        assertThat(request.variableId()).isEqualTo(TEAM);
    }

    @Test
    void refusesATeamLinearDoesNotConfirm() {
        LINEAR.respondWithNoTeam();
        assertThat(client.workspaceOf(TEAM, TOKEN)).isEmpty();

        // Another team's answer never stands in for the one that was asked about.
        LINEAR.respondWithTeam("44444444-4444-4444-8444-444444444444", WORKSPACE);
        assertThat(client.workspaceOf(TEAM, TOKEN)).isEmpty();

        LINEAR.respondWithTeam(TEAM, "");
        assertThat(client.workspaceOf(TEAM, TOKEN)).isEmpty();
    }

    @Test
    void restatesAnIssueOfTheConnectedTeamAndWorkspace() {
        LINEAR.respondWithIssue(ISSUE, "Harden the export", "Details", "Mai Tran",
                "https://linear.app/acme/issue/ENG-123", TEAM, WORKSPACE);

        Optional<LinearIssue> issue = client.issue(ISSUE, TEAM, WORKSPACE, TOKEN);

        assertThat(issue).contains(new LinearIssue(ISSUE, "Harden the export", "Details", "Mai Tran",
                "https://linear.app/acme/issue/ENG-123"));
        LinearStub.RecordedRequest request = LINEAR.requests().getFirst();
        assertThat(request.query()).isEqualTo(
                "query($id:String!){issue(id:$id){id title description url creator{name} team{id organization{id}}}}");
        assertThat(request.variableId()).isEqualTo(ISSUE);
    }

    @Test
    void refusesAnIssueOfAnotherIdentity() {
        LINEAR.respondWithNoIssue();
        assertThat(client.issue(ISSUE, TEAM, WORKSPACE, TOKEN)).isEmpty();

        LINEAR.respondWithIssue("55555555-5555-4555-8555-555555555555", "t", "d", "a", "https://linear.app/i",
                TEAM, WORKSPACE);
        assertThat(client.issue(ISSUE, TEAM, WORKSPACE, TOKEN)).as("another issue").isEmpty();

        LINEAR.respondWithIssue(ISSUE, "t", "d", "a", "https://linear.app/i",
                "66666666-6666-4666-8666-666666666666", WORKSPACE);
        assertThat(client.issue(ISSUE, TEAM, WORKSPACE, TOKEN)).as("another team").isEmpty();

        LINEAR.respondWithIssue(ISSUE, "t", "d", "a", "https://linear.app/i", TEAM,
                "77777777-7777-4777-8777-777777777777");
        assertThat(client.issue(ISSUE, TEAM, WORKSPACE, TOKEN)).as("another workspace").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 503})
    void aFailedCallIsAnEmptyAnswer(int status) {
        LINEAR.fail(status, 1);

        assertThat(client.workspaceOf(TEAM, TOKEN)).isEmpty();
    }

    @Test
    void aTimeoutIsAnEmptyAnswer() {
        LINEAR.respondWithTeam(TEAM, WORKSPACE);
        LINEAR.delay(Duration.ofSeconds(1));

        assertThat(client(Duration.ofMillis(200)).workspaceOf(TEAM, TOKEN)).isEmpty();
    }

    @Test
    void aRedirectIsNotFollowed() {
        LINEAR.redirectTo("https://linear.example.invalid/graphql");

        assertThat(client.workspaceOf(TEAM, TOKEN)).isEmpty();
        assertThat(LINEAR.requests()).hasSize(1);
    }

    @Test
    void refusesToCallLinearInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.workspaceOf(TEAM, TOKEN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must not be called inside a database transaction");
            assertThatThrownBy(() -> client.issue(ISSUE, TEAM, WORKSPACE, TOKEN))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private LinearApiClient client(Duration timeout) {
        return new LinearApiClient(LINEAR.baseUrl(), timeout);
    }
}
