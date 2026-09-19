package com.hoangluongtran0309.releaseflow.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Posts the release note to a Slack channel through an incoming webhook. The webhook
 * URL is the Action's secret; it is checked against Slack's own hosts again here, so
 * a URL that changed in the database cannot send the note anywhere else.
 */
@Component
class SlackActionExecutor implements RuleActionExecutor {

    static final int MAX_MESSAGE_LENGTH = 39_000;
    static final String MESSAGE_TOO_LONG = "slack_message_too_long";
    static final String REJECTED = "slack_rejected";
    static final String WEBHOOK_INVALID = "slack_webhook_invalid";

    private static final Logger log = LoggerFactory.getLogger(SlackActionExecutor.class);

    private final RestClient restClient;
    private final SlackWebhookUrl webhookUrl;

    SlackActionExecutor(SlackWebhookUrl webhookUrl, @Value("${releaseflow.automation.timeout}") Duration timeout) {
        this.webhookUrl = webhookUrl;
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
        return ActionType.SLACK;
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
        String text = "*Release " + command.releaseVersion() + "*\n" + command.noteContent();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return ActionResult.failed(MESSAGE_TOO_LONG);
        }
        try {
            restClient.post().uri(webhook).body(Map.of("text", text)).retrieve().toBodilessEntity();
            return ActionResult.succeeded(null);
        } catch (RestClientResponseException exception) {
            log.warn("Slack refused the note of release {} with HTTP {}.",
                    command.releaseId(), exception.getStatusCode().value());
            return ActionResult.failed(REJECTED);
        } catch (RestClientException exception) {
            // Slack may have posted the message before the connection failed.
            log.warn("Could not reach Slack with the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Slack must not be called inside a database transaction.");
        }
    }
}
