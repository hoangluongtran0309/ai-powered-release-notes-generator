package com.hoangluongtran0309.releaseflow.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The GitHub REST calls ReleaseFlow makes with a Project's access token. Neither call
 * runs inside a database transaction, and neither ever logs the token.
 */
@Component
public class GitHubApiClient {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 5;

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String API_VERSION = "2022-11-28";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    GitHubApiClient(
            @Value("${releaseflow.github.api-base-url}") String baseUrl,
            @Value("${releaseflow.github.timeout}") Duration timeout,
            ObjectMapper objectMapper
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Checks the permission the change worker needs: reading the repository's pull
     * requests, which proves more than being able to see the repository.
     */
    public GitHubAccess checkPullRequestAccess(String owner, String repository, String token) {
        requireNoTransaction();
        try {
            restClient.get()
                    .uri("/repos/{owner}/{repository}/pulls?state=closed&per_page=1", owner, repository)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return GitHubAccess.GRANTED;
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return GitHubAccess.REJECTED;
            }
            log.warn("GitHub returned HTTP {} while checking access to {}/{}.",
                    exception.getStatusCode().value(), owner, repository);
            return GitHubAccess.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Could not reach GitHub while checking access to {}/{}.", owner, repository);
            return GitHubAccess.UNAVAILABLE;
        }
    }

    /**
     * Lists the files of a pull request, up to {@value #MAX_PAGES} pages. A list that may
     * be truncated is reported as unavailable, because a file that was never seen cannot
     * be declared safe.
     */
    public PullRequestFiles pullRequestFiles(String owner, String repository, int number, String token) {
        requireNoTransaction();
        List<ChangedFile> files = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                String body = restClient.get()
                        .uri(
                                "/repos/{owner}/{repository}/pulls/{number}/files?per_page={size}&page={page}",
                                owner,
                                repository,
                                number,
                                PAGE_SIZE,
                                page
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(String.class);
                Optional<List<ChangedFile>> pageFiles = parsePage(body);
                if (pageFiles.isEmpty()) {
                    return unavailable(owner, repository, number, PullRequestFiles.INVALID_RESPONSE, false);
                }
                files.addAll(pageFiles.get());
                if (pageFiles.get().size() < PAGE_SIZE) {
                    return PullRequestFiles.collected(files);
                }
            }
            return unavailable(owner, repository, number, PullRequestFiles.TOO_MANY_FILES, false);
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return unavailable(owner, repository, number, PullRequestFiles.ACCESS_REJECTED, false);
            }
            return unavailable(owner, repository, number, PullRequestFiles.UNAVAILABLE, true);
        } catch (RestClientException exception) {
            return unavailable(owner, repository, number, PullRequestFiles.UNAVAILABLE, true);
        }
    }

    private Optional<List<ChangedFile>> parsePage(String body) {
        try {
            JsonNode page = objectMapper.readTree(body == null ? "" : body);
            if (page == null || !page.isArray()) {
                return Optional.empty();
            }
            List<ChangedFile> files = new ArrayList<>();
            for (JsonNode file : page.values()) {
                JsonNode path = file.path("filename");
                if (!path.isString() || path.stringValue().isBlank()) {
                    return Optional.empty();
                }
                JsonNode previousPath = file.path("previous_filename");
                files.add(new ChangedFile(
                        path.stringValue(),
                        previousPath.isString() && !previousPath.stringValue().isBlank() ? previousPath.stringValue() : null,
                        kind(file.path("status").asString(""))
                ));
            }
            return Optional.of(files);
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }

    private static ChangedFileKind kind(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "added", "copied" -> ChangedFileKind.ADDED;
            case "modified", "changed" -> ChangedFileKind.MODIFIED;
            case "removed" -> ChangedFileKind.REMOVED;
            case "renamed" -> ChangedFileKind.RENAMED;
            default -> ChangedFileKind.OTHER;
        };
    }

    // A rate limit also answers 403 (or 429); only a real refusal is final.
    private static boolean isRejection(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        boolean rateLimited = "0".equals(exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst("x-ratelimit-remaining"));
        return status.value() == 401 || status.value() == 404 || (status.value() == 403 && !rateLimited);
    }

    private static PullRequestFiles unavailable(
            String owner,
            String repository,
            int number,
            String failure,
            boolean retryable
    ) {
        log.warn("Could not list the changed files of {}/{}#{}: {}.", owner, repository, number, failure);
        return PullRequestFiles.unavailable(failure, retryable);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("GitHub must not be called inside a database transaction.");
        }
    }
}
