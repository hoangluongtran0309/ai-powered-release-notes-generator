package com.hoangluongtran0309.releaseflow.gitlab;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The GitLab REST v4 calls ReleaseFlow makes with a source's access token. The instance
 * is the source's own base URL, checked against the deployment's allowlist again before
 * every call; redirects are never followed, so a redirect cannot move a call to another
 * host. No call runs inside a database transaction, and none ever logs the token.
 */
@Component
public class GitLabApiClient {

    static final int PAGE_SIZE = ProviderListing.PAGE_SIZE;
    static final int MAX_DIFF_PAGES = 10;
    static final int MAX_COMMIT_PAGES = 3;
    static final int MAX_COMMITS = 250;

    private static final Logger log = LoggerFactory.getLogger(GitLabApiClient.class);
    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";

    private final GitLabBaseUrl baseUrls;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration timeout;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    GitLabApiClient(
            GitLabBaseUrl baseUrls,
            @Value("${releaseflow.gitlab.timeout}") Duration timeout,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.baseUrls = baseUrls;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Checks that the token can read the project, which is what the change worker and the
     * history import both need.
     */
    public ProviderAccess checkProjectAccess(String baseUrl, String projectKey, String token) {
        requireNoTransaction();
        try {
            client(baseUrl).get()
                    .uri("/api/v4/projects/{project}", projectKey)
                    .header(TOKEN_HEADER, token)
                    .retrieve()
                    .toBodilessEntity();
            return ProviderAccess.GRANTED;
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return ProviderAccess.REJECTED;
            }
            log.warn("GitLab returned HTTP {} while checking access to {}.", exception.getStatusCode().value(), projectKey);
            return ProviderAccess.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Could not reach GitLab while checking access to {}.", projectKey);
            return ProviderAccess.UNAVAILABLE;
        }
    }

    /**
     * Lists the files a merge request changed, up to {@value #MAX_DIFF_PAGES} pages. A
     * diff GitLab collapsed, truncated, or called too large means the list cannot be
     * trusted to be complete, so the whole list is unavailable rather than short.
     */
    public ChangedFiles mergeRequestFiles(String baseUrl, String projectKey, int mergeRequestIid, String token) {
        requireNoTransaction();
        List<ChangedFile> files = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_DIFF_PAGES; page++) {
                String body = client(baseUrl).get()
                        .uri(
                                "/api/v4/projects/{project}/merge_requests/{iid}/diffs?per_page={size}&page={page}",
                                projectKey,
                                mergeRequestIid,
                                PAGE_SIZE,
                                page
                        )
                        .header(TOKEN_HEADER, token)
                        .retrieve()
                        .body(String.class);
                Page parsed = parseDiffPage(body);
                if (parsed.failure() != null) {
                    return unavailable(projectKey, mergeRequestIid, parsed.failure(), false);
                }
                files.addAll(parsed.files());
                if (parsed.size() < PAGE_SIZE) {
                    return ChangedFiles.collected(files);
                }
            }
            return unavailable(projectKey, mergeRequestIid, ChangedFiles.TOO_MANY_FILES, false);
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return unavailable(projectKey, mergeRequestIid, ChangedFiles.ACCESS_REJECTED, false);
            }
            return unavailable(projectKey, mergeRequestIid, ChangedFiles.GITLAB_UNAVAILABLE, true);
        } catch (RestClientException exception) {
            return unavailable(projectKey, mergeRequestIid, ChangedFiles.GITLAB_UNAVAILABLE, true);
        }
    }

    /**
     * One page of the project's merged merge requests, least recently updated first, from
     * {@code updatedAfter}. Asking in that order keeps a page stable while an import runs,
     * so a resumed import reads the same items again.
     */
    public ProviderListing mergedMergeRequests(
            String baseUrl,
            String projectKey,
            int page,
            Instant updatedAfter,
            String token
    ) {
        requireNoTransaction();
        final String body;
        try {
            body = client(baseUrl).get()
                    .uri(
                            "/api/v4/projects/{project}/merge_requests"
                                    + "?state=merged&order_by=updated_at&sort=asc&per_page={size}&page={page}"
                                    + "&updated_after={updatedAfter}",
                            projectKey,
                            PAGE_SIZE,
                            page,
                            updatedAfter.toString()
                    )
                    .header(TOKEN_HEADER, token)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            Optional<Duration> retryAfter = retryAfter(exception);
            if (exception.getStatusCode().value() == 429 || retryAfter.isPresent()) {
                log.warn("GitLab rate-limited listing the merge requests of {}.", projectKey);
                return ProviderListing.failed(ProviderListing.Status.RATE_LIMITED, retryAfter.orElse(null));
            }
            if (isRejection(exception)) {
                log.warn("GitLab refused listing the merge requests of {} (HTTP {}).", projectKey,
                        exception.getStatusCode().value());
                return ProviderListing.failed(ProviderListing.Status.REJECTED, null);
            }
            log.warn("GitLab returned HTTP {} listing the merge requests of {}.", exception.getStatusCode().value(),
                    projectKey);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        } catch (RestClientException exception) {
            log.warn("Could not reach GitLab to list the merge requests of {}.", projectKey);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        }
        try {
            JsonNode items = objectMapper.readTree(body == null ? "" : body);
            if (items == null || !items.isArray()) {
                return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
            }
            List<JsonNode> mergeRequests = new ArrayList<>();
            items.values().forEach(mergeRequests::add);
            return ProviderListing.listed(mergeRequests);
        } catch (JacksonException exception) {
            return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
        }
    }

    /**
     * The messages of a merge request's commits, up to {@value #MAX_COMMIT_PAGES} pages.
     * They are read only to find issue keys, so a list that could not be read is empty
     * rather than an error the change waits on.
     */
    public Optional<List<String>> mergeRequestCommits(
            String baseUrl,
            String projectKey,
            int mergeRequestIid,
            String token
    ) {
        requireNoTransaction();
        List<String> messages = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_COMMIT_PAGES && messages.size() < MAX_COMMITS; page++) {
                String body = client(baseUrl).get()
                        .uri(
                                "/api/v4/projects/{project}/merge_requests/{iid}/commits?per_page={size}&page={page}",
                                projectKey,
                                mergeRequestIid,
                                PAGE_SIZE,
                                page
                        )
                        .header(TOKEN_HEADER, token)
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
                    messages.add(commit.path("message").asString(""));
                }
                if (commits.size() < PAGE_SIZE) {
                    break;
                }
            }
            return Optional.of(List.copyOf(messages));
        } catch (RestClientException | JacksonException exception) {
            log.warn("Could not list the commits of {}!{}.", projectKey, mergeRequestIid);
            return Optional.empty();
        }
    }

    private Page parseDiffPage(String body) {
        final JsonNode page;
        try {
            page = objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException exception) {
            return Page.failed(ChangedFiles.INVALID_RESPONSE);
        }
        if (page == null || !page.isArray()) {
            return Page.failed(ChangedFiles.INVALID_RESPONSE);
        }
        List<ChangedFile> files = new ArrayList<>();
        int size = 0;
        for (JsonNode diff : page.values()) {
            size++;
            if (diff.path("collapsed").booleanValue(false)
                    || diff.path("too_large").booleanValue(false)
                    || diff.path("truncated").booleanValue(false)) {
                return Page.failed(ChangedFiles.DIFF_UNAVAILABLE);
            }
            String newPath = diff.path("new_path").asString("");
            String oldPath = diff.path("old_path").asString("");
            if (newPath.isBlank() && oldPath.isBlank()) {
                return Page.failed(ChangedFiles.INVALID_RESPONSE);
            }
            // GitLab repeats the path on both sides unless the file really moved.
            String path = newPath.isBlank() ? oldPath : newPath;
            files.add(new ChangedFile(
                    path,
                    oldPath.isBlank() || oldPath.equals(path) ? null : oldPath,
                    kind(diff)
            ));
        }
        return Page.parsed(files, size);
    }

    private static ChangedFileKind kind(JsonNode diff) {
        if (diff.path("new_file").booleanValue(false)) {
            return ChangedFileKind.ADDED;
        }
        if (diff.path("deleted_file").booleanValue(false)) {
            return ChangedFileKind.REMOVED;
        }
        if (diff.path("renamed_file").booleanValue(false)) {
            return ChangedFileKind.RENAMED;
        }
        return ChangedFileKind.MODIFIED;
    }

    // Retry-After in seconds, or the time until GitLab's rate-limit window resets.
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
                // A date is allowed by HTTP but not sent by GitLab; fall back to the reset time.
            }
        }
        String reset = headers.getFirst("ratelimit-reset");
        if (reset != null) {
            try {
                long seconds = Long.parseLong(reset.strip()) - clock.instant().getEpochSecond();
                return Optional.of(Duration.ofSeconds(Math.max(0, seconds)));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isRejection(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        return status.value() == 401 || status.value() == 403 || status.value() == 404;
    }

    // The allowlist is checked again here, so a stored base URL can never outlive it.
    private RestClient client(String baseUrl) {
        String validated = baseUrls.validated(baseUrl);
        return clients.computeIfAbsent(validated, url -> {
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .connectTimeout(timeout)
                            .build()
            );
            requestFactory.setReadTimeout(timeout);
            return RestClient.builder()
                    .baseUrl(url)
                    .requestFactory(requestFactory)
                    .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                    .build();
        });
    }

    private static ChangedFiles unavailable(String projectKey, int mergeRequestIid, String failure, boolean retryable) {
        log.warn("Could not list the changed files of {}!{}: {}.", projectKey, mergeRequestIid, failure);
        return ChangedFiles.unavailable(failure, retryable);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("GitLab must not be called inside a database transaction.");
        }
    }

    private record Page(List<ChangedFile> files, int size, String failure) {

        static Page parsed(List<ChangedFile> files, int size) {
            return new Page(files, size, null);
        }

        static Page failed(String failure) {
            return new Page(List.of(), 0, failure);
        }
    }
}
