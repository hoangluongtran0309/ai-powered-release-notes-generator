package com.hoangluongtran0309.releaseflow.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Posts the release note into a Teams chat or channel through a Workflows callback. Like
 * Slack it leaves nothing behind, so a rule ReleaseFlow sets off itself may use it.
 *
 * <p>The callback URL is the action's secret and carries its own signature, so it is
 * checked against the Workflows shape again here: a URL that changed in the database
 * cannot send the note anywhere else.
 */
@Component
class TeamsActionExecutor implements RuleActionExecutor {

    static final int MAX_PAYLOAD_BYTES = 28 * 1024;
    static final String PAYLOAD_TOO_LARGE = "teams_message_too_long";
    static final String REJECTED = "teams_rejected";
    static final String WEBHOOK_INVALID = "teams_webhook_invalid";

    private static final Logger log = LoggerFactory.getLogger(TeamsActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TeamsWebhookUrl webhookUrl;
    private final String apiBaseUrl;

    TeamsActionExecutor(
            ObjectMapper objectMapper,
            TeamsWebhookUrl webhookUrl,
            @Value("${releaseflow.automation.teams.api-base-url:}") String apiBaseUrl,
            @Value("${releaseflow.automation.timeout}") Duration timeout
    ) {
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? null : withoutTrailingSlashes(apiBaseUrl.strip());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // The URL is a credential: a redirect would hand it to another origin.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ActionType actionType() {
        return ActionType.MICROSOFT_TEAMS;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        webhookUrl.validated(rawSecret);
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        requireNoTransaction();
        final URI webhook;
        try {
            webhook = webhookUrl.validated(command.rawSecret());
        } catch (AutomationActionInvalidException exception) {
            return ActionResult.failed(WEBHOOK_INVALID);
        }
        // Teams measures the request, not the message, so the payload is written first.
        final byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(Map.of(
                    "text", "Release " + command.releaseVersion() + "\n" + command.noteContent()));
        } catch (JacksonException exception) {
            log.warn("Could not write the Teams message for release {}.", command.releaseId());
            return ActionResult.failed(REJECTED);
        }
        if (payload.length > MAX_PAYLOAD_BYTES) {
            return ActionResult.failed(PAYLOAD_TOO_LARGE);
        }
        final URI address;
        try {
            address = callAddress(webhook);
        } catch (URISyntaxException exception) {
            return ActionResult.failed(WEBHOOK_INVALID);
        }
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(address)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode().is2xxSuccessful()) {
                return ActionResult.succeeded(null);
            }
            // A redirect is not followed, so the message went nowhere.
            log.warn("Microsoft Teams answered HTTP {} for the note of release {}.",
                    response.getStatusCode().value(), command.releaseId());
            return ActionResult.failed(REJECTED);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (exception.getStatusCode().is5xxServerError()) {
                // Teams may have accepted the message before it failed to answer.
                log.warn("Microsoft Teams answered HTTP {} for the note of release {}.",
                        status, command.releaseId());
                return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
            }
            log.warn("Microsoft Teams refused the note of release {} with HTTP {}.", command.releaseId(), status);
            return ActionResult.failed(REJECTED);
        } catch (RestClientException exception) {
            // Teams may have posted the message before the connection failed.
            log.warn("Could not reach Microsoft Teams with the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        }
    }

    /**
     * Where the call goes: the callback itself, unless the deployment stands in for the
     * Power Platform, which replaces the origin and keeps the path and the signature.
     */
    private URI callAddress(URI webhook) throws URISyntaxException {
        if (apiBaseUrl == null) {
            return webhook;
        }
        URI stand = new URI(apiBaseUrl);
        return new URI(
                stand.getScheme(),
                null,
                stand.getHost(),
                stand.getPort(),
                webhook.getPath(),
                webhook.getQuery(),
                null
        );
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
            throw new IllegalStateException("Microsoft Teams must not be called inside a database transaction.");
        }
    }
}
