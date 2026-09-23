package com.hoangluongtran0309.releaseflow.linear;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The Linear GraphQL calls ReleaseFlow makes with a source's API token. Linear has one
 * endpoint for every workspace, and it takes the token bare, with no {@code Bearer}
 * prefix. Redirects are never followed, no call runs inside a database transaction, and
 * none ever logs the token.
 */
@Component
public class LinearApiClient {

    private static final Logger log = LoggerFactory.getLogger(LinearApiClient.class);
    private static final String TEAM_QUERY = "query($id:String!){team(id:$id){id organization{id}}}";
    private static final String ISSUE_QUERY =
            "query($id:String!){issue(id:$id){id title description url creator{name} team{id organization{id}}}}";

    private final RestClient restClient;

    LinearApiClient(
            @Value("${releaseflow.linear.api-base-url}") String baseUrl,
            @Value("${releaseflow.linear.timeout}") Duration timeout
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(timeout)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * The workspace the team belongs to, if the token can read that team and Linear
     * answers with the very team that was asked for.
     */
    public Optional<String> workspaceOf(String teamId, String token) {
        requireNoTransaction();
        JsonNode team = query(TEAM_QUERY, teamId, token)
                .map(data -> data.path("team"))
                .orElse(null);
        if (team == null || !teamId.equals(team.path("id").asString(""))) {
            return Optional.empty();
        }
        String workspace = team.path("organization").path("id").asString("");
        return workspace.isBlank() ? Optional.empty() : Optional.of(workspace);
    }

    /**
     * The issue as Linear has it now, but only when it really is the issue of the team
     * and workspace this source is connected to.
     */
    public Optional<LinearIssue> issue(String issueId, String teamId, String workspaceId, String token) {
        requireNoTransaction();
        JsonNode issue = query(ISSUE_QUERY, issueId, token)
                .map(data -> data.path("issue"))
                .orElse(null);
        if (issue == null
                || !issueId.equals(issue.path("id").asString(""))
                || !teamId.equals(issue.path("team").path("id").asString(""))
                || !workspaceId.equals(issue.path("team").path("organization").path("id").asString(""))) {
            return Optional.empty();
        }
        return Optional.of(new LinearIssue(
                issue.path("id").asString(""),
                issue.path("title").asString(""),
                issue.path("description").asString(""),
                issue.path("creator").path("name").asString(""),
                issue.path("url").asString("")
        ));
    }

    // Linear answers 200 with an "errors" array rather than an HTTP status, so a
    // response without the data it was asked for is simply an empty answer.
    private Optional<JsonNode> query(String document, String id, String token) {
        try {
            JsonNode response = restClient.post()
                    .uri("/graphql")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .body(Map.of("query", document, "variables", Map.of("id", id)))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("data").isObject()) {
                return Optional.empty();
            }
            return Optional.of(response.path("data"));
        } catch (RestClientException exception) {
            log.warn("Could not reach Linear.");
            return Optional.empty();
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Linear must not be called inside a database transaction.");
        }
    }
}
