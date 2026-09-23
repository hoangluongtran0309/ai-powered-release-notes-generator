package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Publishes the release note as a Help Center article. Two calls make one delivery: a
 * short-lived token from Zendesk's own OAuth endpoint, then the article. The token lives
 * in a local variable for the length of the delivery and is never stored or logged.
 *
 * <p>The two calls fail differently on purpose. Nothing is published until the second
 * one, so every way the first can fail is {@code FAILED}, even a server error: repeating
 * it cannot duplicate an article that was never created. The second follows the usual
 * rule, because by then an article may exist.
 */
@Component
class ZendeskActionExecutor implements RuleActionExecutor {

    static final int MAX_ARTICLE_BYTES = 1_000_000;
    static final String CONTENT_TOO_LARGE = "zendesk_content_too_large";
    static final String REJECTED = "zendesk_rejected";
    static final String AUTH_REJECTED = "zendesk_auth_rejected";
    static final String AUTH_UNAVAILABLE = "zendesk_auth_unavailable";
    static final String CONFIGURATION_INVALID = "zendesk_configuration_invalid";

    private static final Logger log = LoggerFactory.getLogger(ZendeskActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;

    ZendeskActionExecutor(
            ObjectMapper objectMapper,
            @Value("${releaseflow.automation.zendesk.api-base-url:}") String apiBaseUrl,
            @Value("${releaseflow.automation.timeout}") Duration timeout
    ) {
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? null
                : withoutTrailingSlashes(apiBaseUrl.strip());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // A redirect proves nothing about who answered, so it is never followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ActionType actionType() {
        return ActionType.ZENDESK;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        ZendeskTenant.from(configuration);
        if (rawSecret == null || rawSecret.isBlank()) {
            throw AutomationActionInvalidException.zendeskClientSecretRequired();
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        requireNoTransaction();
        final ZendeskTenant tenant;
        try {
            validate(command.configuration(), command.rawSecret());
            tenant = ZendeskTenant.from(command.configuration());
        } catch (AutomationActionInvalidException exception) {
            return ActionResult.failed(CONFIGURATION_INVALID);
        }
        String articleBody = MarkdownHtml.render(command.noteContent());
        if (articleBody.getBytes(StandardCharsets.UTF_8).length > MAX_ARTICLE_BYTES) {
            return ActionResult.failed(CONTENT_TOO_LARGE);
        }
        Token token = token(tenant, command);
        if (token.failure() != null) {
            return token.failure();
        }
        return publish(tenant, command, articleBody, token.accessToken());
    }

    /** The first call. Nothing is published yet, so nothing here is ever unknown. */
    private Token token(ZendeskTenant tenant, ActionCommand command) {
        String form = "grant_type=client_credentials"
                + "&client_id=" + encoded(tenant.clientId())
                + "&client_secret=" + encoded(command.rawSecret())
                + "&scope=write";
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(callAddress(tenant) + "/oauth/tokens")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                int status = response.getStatusCode().value();
                log.warn("Zendesk answered HTTP {} for the release {} access token.", status, command.releaseId());
                return Token.failed(ActionResult.failed(AUTH_REJECTED));
            }
            String accessToken = accessToken(response.getBody());
            if (accessToken == null) {
                log.warn("Zendesk answered the release {} token request without a token.", command.releaseId());
                return Token.failed(ActionResult.failed(AUTH_UNAVAILABLE));
            }
            return Token.of(accessToken);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("Zendesk refused the release {} access token with HTTP {}.", command.releaseId(), status);
            return Token.failed(ActionResult.failed(
                    exception.getStatusCode().is5xxServerError() ? AUTH_UNAVAILABLE : AUTH_REJECTED));
        } catch (RestClientException exception) {
            // Nothing was published, so trying again later cannot duplicate anything.
            log.warn("Could not reach Zendesk for the release {} access token.", command.releaseId());
            return Token.failed(ActionResult.failed(AUTH_UNAVAILABLE));
        }
    }

    /** The second call. From here an article may exist whatever the answer says. */
    private ActionResult publish(ZendeskTenant tenant, ActionCommand command, String articleBody, String accessToken) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "Release " + command.releaseVersion());
        article.put("body", articleBody);
        article.put("locale", command.language().toLowerCase(Locale.ROOT));
        article.put("draft", false);
        if (tenant.userSegmentId() != null) {
            article.put("user_segment_id", Long.parseLong(tenant.userSegmentId()));
        }
        Map<String, Object> body = Map.of("article", article, "notify_subscribers", false);
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(callAddress(tenant) + "/api/v2/help_center/sections/" + tenant.sectionId() + "/articles.json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String url = articleUrl(response.getBody());
                // An answer that names no article leaves nobody able to say whether one exists.
                return url == null ? ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN) : ActionResult.succeeded(url);
            }
            // A redirect is not followed, so no article was written wherever it pointed.
            log.warn("Zendesk answered HTTP {} for the note of release {}.",
                    response.getStatusCode().value(), command.releaseId());
            return ActionResult.failed(REJECTED);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (exception.getStatusCode().is5xxServerError()) {
                // Zendesk may have created the article before it failed to answer.
                log.warn("Zendesk answered HTTP {} for the note of release {}.", status, command.releaseId());
                return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
            }
            log.warn("Zendesk refused the note of release {} with HTTP {}.", command.releaseId(), status);
            return ActionResult.failed(REJECTED);
        } catch (RestClientException exception) {
            // Zendesk may have created the article before the connection failed.
            log.warn("Could not reach Zendesk with the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        }
    }

    /** Where the calls go: the help centre itself, unless the deployment stands in for it. */
    private String callAddress(ZendeskTenant tenant) {
        return apiBaseUrl == null ? tenant.origin() : apiBaseUrl;
    }

    private String accessToken(String body) {
        try {
            JsonNode answer = objectMapper.readTree(body == null ? "" : body);
            String token = answer == null ? "" : answer.path("access_token").asString("");
            return token.isBlank() ? null : token;
        } catch (JacksonException exception) {
            return null;
        }
    }

    /** The article as a person would open it, which is also proof one was created. */
    private String articleUrl(String body) {
        try {
            JsonNode article = objectMapper.readTree(body == null ? "" : body).path("article");
            String url = article.path("html_url").asString("");
            return article.path("id").asLong(0) > 0 && !url.isBlank() ? url : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String withoutTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Zendesk must not be called inside a database transaction.");
        }
    }

    /** Either a token to publish with, or the result that ends the delivery. */
    private record Token(String accessToken, ActionResult failure) {

        static Token of(String accessToken) {
            return new Token(accessToken, null);
        }

        static Token failed(ActionResult failure) {
            return new Token(null, failure);
        }
    }
}
