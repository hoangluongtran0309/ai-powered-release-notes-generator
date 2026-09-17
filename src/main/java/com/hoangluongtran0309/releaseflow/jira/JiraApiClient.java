package com.hoangluongtran0309.releaseflow.jira;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Jira Cloud REST calls ReleaseFlow makes with a source's API token. Jira takes the
 * account's email and the token as Basic credentials. The site is checked against the
 * Atlassian Cloud rules again before every call, redirects are never followed, no call
 * runs inside a database transaction, and none ever logs the token.
 *
 * <p>A deployment may send every call to one fixed address instead of the site, which is
 * how tests and local runs stand in for Jira Cloud. Only configuration can set it; the
 * site a request names is still checked, and still builds every issue link.
 */
@Component
public class JiraApiClient {

    static final int PAGE_SIZE = ProviderListing.PAGE_SIZE;
    // Jira's JQL takes minutes, not seconds, and reads them in the site's UTC offset.
    static final DateTimeFormatter JQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final String SEARCH_FIELDS = "summary,description,reporter,updated,resolutiondate";
    private static final String ISSUE_FIELDS = "summary,description,issuetype,status";

    private static final Logger log = LoggerFactory.getLogger(JiraApiClient.class);

    private final JiraSiteUrl siteUrls;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final int descriptionLimit;
    private final String apiBaseUrl;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    JiraApiClient(
            JiraSiteUrl siteUrls,
            @Value("${releaseflow.jira.timeout}") Duration timeout,
            @Value("${releaseflow.jira.description-max-characters}") int descriptionLimit,
            @Value("${releaseflow.jira.api-base-url:}") String apiBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.siteUrls = siteUrls;
        this.timeout = timeout;
        this.descriptionLimit = descriptionLimit;
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? null : apiBaseUrl.strip().replaceAll("/+$", "");
        this.objectMapper = objectMapper;
    }

    /** Checks that the account and token can read the project ReleaseFlow was given. */
    public ProviderAccess checkProjectAccess(String site, String projectKey, String email, String token) {
        requireNoTransaction();
        try {
            HttpStatusCode status = client(site).get()
                    .uri("/rest/api/3/project/{key}", projectKey)
                    .header(HttpHeaders.AUTHORIZATION, basic(email, token))
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode();
            if (status.is2xxSuccessful()) {
                return ProviderAccess.GRANTED;
            }
            // A redirect is not followed, so it confirms nothing.
            log.warn("Jira answered HTTP {} while checking access to {}.", status.value(), projectKey);
            return ProviderAccess.UNAVAILABLE;
        } catch (RestClientResponseException exception) {
            if (isRejection(exception)) {
                return ProviderAccess.REJECTED;
            }
            log.warn("Jira returned HTTP {} while checking access to {}.", exception.getStatusCode().value(), projectKey);
            return ProviderAccess.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Could not reach Jira while checking access to {}.", projectKey);
            return ProviderAccess.UNAVAILABLE;
        }
    }

    /**
     * One page of the project's issues that are done, least recently updated first, from
     * {@code updatedAfter}. Jira pages with an opaque token rather than a page number.
     */
    public ProviderListing completedIssues(
            String site,
            String projectKey,
            String email,
            String token,
            Instant updatedAfter,
            String pageToken
    ) {
        requireNoTransaction();
        String jql = "project = \"" + projectKey.replace("\"", "") + "\""
                + " AND statusCategory = Done"
                + " AND updated >= \"" + JQL_TIME.format(updatedAfter) + "\""
                + " ORDER BY updated ASC";
        final String body;
        try {
            body = client(site).get()
                    .uri(builder -> {
                        builder.path("/rest/api/3/search/jql")
                                .queryParam("jql", jql)
                                .queryParam("maxResults", PAGE_SIZE)
                                .queryParam("fields", SEARCH_FIELDS);
                        if (pageToken != null && !pageToken.isBlank()) {
                            builder.queryParam("nextPageToken", pageToken);
                        }
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, basic(email, token))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            Optional<Duration> retryAfter = retryAfter(exception);
            if (exception.getStatusCode().value() == 429 || retryAfter.isPresent()) {
                log.warn("Jira rate-limited the search of {}.", projectKey);
                return ProviderListing.failed(ProviderListing.Status.RATE_LIMITED, retryAfter.orElse(null));
            }
            if (isRejection(exception)) {
                log.warn("Jira refused the search of {} (HTTP {}).", projectKey, exception.getStatusCode().value());
                return ProviderListing.failed(ProviderListing.Status.REJECTED, null);
            }
            log.warn("Jira returned HTTP {} searching {}.", exception.getStatusCode().value(), projectKey);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        } catch (RestClientException exception) {
            log.warn("Could not reach Jira to search {}.", projectKey);
            return ProviderListing.failed(ProviderListing.Status.UNAVAILABLE, null);
        }
        try {
            JsonNode response = objectMapper.readTree(body == null ? "" : body);
            if (response == null || !response.path("issues").isArray()) {
                return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
            }
            List<JsonNode> issues = new ArrayList<>();
            response.path("issues").values().forEach(issues::add);
            return ProviderListing.paged(issues, response.path("nextPageToken").asString(""));
        } catch (JacksonException exception) {
            return ProviderListing.failed(ProviderListing.Status.INVALID_RESPONSE, null);
        }
    }

    /** One issue a change mentioned, or nothing when Jira will not show it. */
    public Optional<LinkedIssue> issue(String site, String key, String email, String token) {
        requireNoTransaction();
        try {
            String body = client(site).get()
                    .uri("/rest/api/3/issue/{key}?fields={fields}", key, ISSUE_FIELDS)
                    .header(HttpHeaders.AUTHORIZATION, basic(email, token))
                    .retrieve()
                    .body(String.class);
            JsonNode issue = objectMapper.readTree(body == null ? "" : body);
            if (issue == null || !issue.path("fields").isObject()) {
                return Optional.empty();
            }
            JsonNode fields = issue.path("fields");
            return Optional.of(new LinkedIssue(
                    key,
                    fields.path("summary").asString(""),
                    JiraAdfText.convert(fields.path("description"), descriptionLimit),
                    fields.path("issuetype").path("name").asString(""),
                    fields.path("status").path("name").asString(""),
                    siteUrls.validated(site) + "/browse/" + key
            ));
        } catch (RestClientException | JacksonException exception) {
            log.warn("Could not read the Jira issue {}.", key);
            return Optional.empty();
        }
    }

    /** How long Jira asked ReleaseFlow to wait, when it said so. */
    private static Optional<Duration> retryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(retryAfter.strip()))));
        } catch (NumberFormatException exception1) {
            return Optional.empty();
        }
    }

    private static boolean isRejection(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        return status.value() == 401 || status.value() == 403 || status.value() == 404;
    }

    private static String basic(String email, String token) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
    }

    // The site rules are checked again here, so a stored site can never outlive them.
    private RestClient client(String site) {
        String validated = siteUrls.validated(site);
        return clients.computeIfAbsent(apiBaseUrl == null ? validated : apiBaseUrl, url -> {
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

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Jira must not be called inside a database transaction.");
        }
    }
}
