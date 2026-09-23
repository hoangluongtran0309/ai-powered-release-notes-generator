package com.hoangluongtran0309.releaseflow.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Files the release note as a child page under a Notion page an administrator chose.
 * Notion is one known host the deployment configures, so unlike Slack there is no
 * address here that a request could choose: only the parent page and the integration
 * token come from the Action.
 */
@Component
class NotionActionExecutor implements RuleActionExecutor {

    static final int MAX_CONTENT_BYTES = 450_000;
    static final String CONTENT_TOO_LARGE = "notion_content_too_large";
    static final String REJECTED = "notion_rejected";
    static final String CONFIGURATION_INVALID = "notion_configuration_invalid";

    private static final Logger log = LoggerFactory.getLogger(NotionActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiVersion;

    NotionActionExecutor(
            ObjectMapper objectMapper,
            @Value("${releaseflow.automation.notion.api-base-url}") String apiBaseUrl,
            @Value("${releaseflow.automation.notion.version}") String apiVersion,
            @Value("${releaseflow.automation.timeout}") Duration timeout
    ) {
        this.objectMapper = objectMapper;
        this.apiVersion = apiVersion;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // A redirect proves nothing about who answered, so it is never followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ActionType actionType() {
        return ActionType.NOTION;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        NotionParentPage.normalized(configuration.get(NotionParentPage.KEY));
        if (rawSecret == null || rawSecret.isBlank()) {
            throw AutomationActionInvalidException.notionTokenRequired();
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        requireNoTransaction();
        final String parentPageId;
        try {
            validate(command.configuration(), command.rawSecret());
            parentPageId = NotionParentPage.normalized(command.configuration().get(NotionParentPage.KEY));
        } catch (AutomationActionInvalidException exception) {
            return ActionResult.failed(CONFIGURATION_INVALID);
        }
        String content = command.noteContent();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            return ActionResult.failed(CONTENT_TOO_LARGE);
        }
        Map<String, Object> body = Map.of(
                "parent", Map.of("type", "page_id", "page_id", parentPageId),
                "properties", Map.of(
                        "title", List.of(Map.of("text", Map.of("content", title(command))))),
                "markdown", content
        );
        try {
            ResponseEntity<String> created = restClient.post()
                    .uri("/v1/pages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + command.rawSecret())
                    .header("Notion-Version", apiVersion)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (created.getStatusCode().is2xxSuccessful()) {
                return ActionResult.succeeded(pageUrl(created.getBody()));
            }
            // A redirect is not followed, so no page was written wherever it pointed.
            log.warn("Notion answered HTTP {} for the note of release {}.",
                    created.getStatusCode().value(), command.releaseId());
            return ActionResult.failed(REJECTED);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (exception.getStatusCode().is5xxServerError()) {
                // Notion may have created the page before it failed to answer.
                log.warn("Notion answered HTTP {} for the note of release {}.", status, command.releaseId());
                return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
            }
            log.warn("Notion refused the note of release {} with HTTP {}.", command.releaseId(), status);
            return ActionResult.failed(REJECTED);
        } catch (RestClientException exception) {
            // Notion may have created the page before the connection failed.
            log.warn("Could not reach Notion with the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        }
    }

    /** The page Notion says it created, or nothing: the delivery happened either way. */
    private String pageUrl(String body) {
        try {
            JsonNode page = objectMapper.readTree(body == null ? "" : body);
            String url = page == null ? "" : page.path("url").asString("");
            return url.isBlank() ? null : url;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String title(ActionCommand command) {
        return "Release " + command.releaseVersion();
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Notion must not be called inside a database transaction.");
        }
    }
}
