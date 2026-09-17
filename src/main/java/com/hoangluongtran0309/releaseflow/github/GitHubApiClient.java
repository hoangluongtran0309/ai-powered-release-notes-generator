package com.hoangluongtran0309.releaseflow.github;

import com.hoangluongtran0309.releaseflow.source.ChangedFile;
import com.hoangluongtran0309.releaseflow.source.ChangedFileKind;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.ProviderAccess;
import com.hoangluongtran0309.releaseflow.source.ProviderListing;
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
import java.time.Clock;
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

    static final int PAGE_SIZE = ProviderListing.PAGE_SIZE;
    static final int MAX_PAGES = 5;
    static final int MAX_COMMIT_PAGES = 3;
    static final int MAX_COMMITS = 250;

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String API_VERSION = "2022-11-28";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GitHubApiClient(
            @Value("${releaseflow.github.api-base-url}") String baseUrl,
            @Value("${releaseflow.github.timeout}") Duration timeout,
            ObjectMapper objectMapper,
            Clock clock
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
        this.clock = clock;
    }

    /**
     * Checks the permission the change worker needs: reading the repository's pull
     * requests, which proves more than being able to see the repository.
     */
    public ProviderAccess checkPullRequestAccess(String owner, String repository, String token) {
        requireNoTransaction();
        try {
            restClient.get()
                    .uri("/repos/{owner}/{repository}/pulls?state=closed&per_page=1", owner, repository)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
            return ProviderAccess.GRANTED;
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return ProviderAccess.REJECTED;
            }
            log.warn("GitHub returned HTTP {} while checking access to {}/{}.",
                    exception.getStatusCode().value(), owner, repository);
            return ProviderAccess.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Could not reach GitHub while checking access to {}/{}.", owner, repository);
            return ProviderAccess.UNAVAILABLE;
        }
    }

    /**
     * Lists the files of a pull request, up to {@value #MAX_PAGES} pages. A list that may
     * be truncated is reported as unavailable, because a file that was never seen cannot
     * be declared safe.
     */
    public ChangedFiles pullRequestFiles(String owner, String repository, int number, String token) {
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
                    return unavailable(owner, repository, number, ChangedFiles.INVALID_RESPONSE, false);
                }
                files.addAll(pageFiles.get());
                if (pageFiles.get().size() < PAGE_SIZE) {
                    return ChangedFiles.collected(files);
                }
            }
            return unavailable(owner, repository, number, ChangedFiles.TOO_MANY_FILES, false);
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return unavailable(owner, repository, number, ChangedFiles.ACCESS_REJECTED, false);
            }
            return unavailable(owner, repository, number, ChangedFiles.GITHUB_UNAVAILABLE, true);
        } catch (RestClientException exception) {
            return unavailable(owner, repository, number, ChangedFiles.GITHUB_UNAVAILABLE, true);
        }
    }

    /**
     * One page of the repository's closed pull requests, most recently updated first.
     * Merged and unmerged ones both appear; the caller keeps the merged ones.
     */
    public ProviderListing closedPullRequests(String owner, String repository, int page, String token) {
        requireNoTransaction();
        final String body;
        try {
            body = restClient.get()
                    .uri(
                            "/repos/{owner}/{repository}/pulls?state=closed&sort=updated&direction=desc&per_page={size}&page={page}",
                            owner,
                            repository,
                            PAGE_SIZE,
                            page
                    )
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            Optional<Duration> retryAfter = retryAfter(exception);
            if (exception.getStatusCode().value() == 429 || retryAfter.isPresent() || isRateLimited(exception)) {
                log.warn("GitHub rate-limited listing the pull requests of {}/{}.", owner, repository);
                return ProviderListing.failed(ProviderListing.Status.RATE_LIMITED, retryAfter.orElse(null));
            }
            if (isRejection(exception)) {
                log.warn("GitHub refused listing the pull requests of {}/{} (HTTP {}).", owner, repository,
                        exception.getStatusCode().value());
                return ProviderListing.failed(ProviderListing.Status.REJECTED, null);
            }
            log.warn("GitHub returned HTTP {} listing the pull requests of {}/{}.", exception.getStatusCode().value(),
                    owner, repository);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        } catch (RestClientException exception) {
            log.warn("Could not reach GitHub to list the pull requests of {}/{}.", owner, repository);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        }
        try {
            JsonNode items = objectMapper.readTree(body == null ? "" : body);
            if (items == null || !items.isArray()) {
                return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
            }
            List<JsonNode> pullRequests = new ArrayList<>();
            items.values().forEach(pullRequests::add);
            return ProviderListing.listed(pullRequests);
        } catch (JacksonException exception) {
            return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
        }
    }

    /**
     * The messages of a pull request's commits, up to {@value #MAX_COMMIT_PAGES} pages.
     * They are read only to find issue keys, so a list that could not be read is empty
     * rather than an error the change waits on.
     */
    public Optional<List<String>> pullRequestCommits(String owner, String repository, int number, String token) {
        requireNoTransaction();
        List<String> messages = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_COMMIT_PAGES && messages.size() < MAX_COMMITS; page++) {
                String body = restClient.get()
                        .uri(
                                "/repos/{owner}/{repository}/pulls/{number}/commits?per_page={size}&page={page}",
                                owner,
                                repository,
                                number,
                                PAGE_SIZE,
                                page
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(String.class);
                JsonNode commits = objectMapper.readTree(body == null ? "" : body);
                if (commits == null || !commits.isArray()) {
                    return Optional.empty();
                }
                for (JsonNode commit : commits.values()) {
                    if (messages.size() == MAX_COMMITS) {
                        break;
                    }
                    messages.add(commit.path("commit").path("message").asString(""));
                }
                if (commits.size() < PAGE_SIZE) {
                    break;
                }
            }
            return Optional.of(List.copyOf(messages));
        } catch (RestClientException | JacksonException exception) {
            log.warn("Could not list the commits of {}/{}#{}.", owner, repository, number);
            return Optional.empty();
        }
    }

    // Retry-After in seconds, or the time until the rate-limit window resets.
    private Optional<Duration> retryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        if (headers == null) {
            return Optional.empty();
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter.strip()))));
            } catch (NumberFormatException ignored) {
                // A date is allowed by HTTP but not sent by GitHub; fall back to the reset time.
            }
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (isRateLimited(exception) && reset != null) {
            try {
                long seconds = Long.parseLong(reset.strip()) - clock.instant().getEpochSecond();
                return Optional.of(Duration.ofSeconds(Math.max(0, seconds)));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isRateLimited(RestClientResponseException exception) {
        return "0".equals(exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst("x-ratelimit-remaining"));
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

    private static ChangedFiles unavailable(
            String owner,
            String repository,
            int number,
            String failure,
            boolean retryable
    ) {
        log.warn("Could not list the changed files of {}/{}#{}: {}.", owner, repository, number, failure);
        return ChangedFiles.unavailable(failure, retryable);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("GitHub must not be called inside a database transaction.");
        }
    }
}
