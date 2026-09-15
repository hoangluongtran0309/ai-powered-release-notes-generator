package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.GitHubWebhookVerifier;
import com.hoangluongtran0309.releaseflow.project.VerifiedGitHubWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

@Service
class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);

    private final GitHubWebhookVerifier verifier;
    private final ChangeIntake intake;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GitHubWebhookService(
            GitHubWebhookVerifier verifier,
            ChangeIntake intake,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.verifier = verifier;
        this.intake = intake;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    WebhookOutcome receive(String webhookId, String signature, String event, String deliveryId, byte[] body) {
        // Nothing from the request is trusted before this integration's own secret verifies it.
        VerifiedGitHubWebhook webhook = verifier.verify(webhookId, signature, body)
                .orElseThrow(WebhookSignatureInvalidException::new);
        if (event == null || event.isBlank()) {
            throw new MalformedWebhookPayloadException("The X-GitHub-Event header is required.");
        }
        JsonNode payload = parse(body);
        requireConfiguredRepository(webhook, event, payload);

        WebhookOutcome outcome = switch (event) {
            case "ping" -> WebhookOutcome.PONG;
            case "pull_request" -> isMerged(payload)
                    ? recordChange(webhook, MergedPullRequest.from(payload.path("pull_request")), parseDeliveryId(deliveryId))
                    : WebhookOutcome.IGNORED;
            default -> WebhookOutcome.IGNORED;
        };
        verifier.recordDelivery(webhook, clock.instant());
        return outcome;
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                throw new MalformedWebhookPayloadException("The webhook payload must be a JSON object.");
            }
            return payload;
        } catch (JacksonException exception) {
            throw new MalformedWebhookPayloadException("The webhook payload is not valid JSON.");
        }
    }

    private static void requireConfiguredRepository(VerifiedGitHubWebhook webhook, String event, JsonNode payload) {
        JsonNode fullName = payload.path("repository").path("full_name");
        if (fullName.isMissingNode() || fullName.isNull()) {
            // Organization-level pings carry no repository; a pull request always must.
            if ("pull_request".equals(event)) {
                throw new WebhookRepositoryMismatchException();
            }
            return;
        }
        if (!fullName.isString() || !webhook.isRepository(fullName.stringValue())) {
            throw new WebhookRepositoryMismatchException();
        }
    }

    private static boolean isMerged(JsonNode payload) {
        return "closed".equals(payload.path("action").asString(""))
                && payload.path("pull_request").path("merged").booleanValue(false);
    }

    private static UUID parseDeliveryId(String deliveryId) {
        if (deliveryId != null) {
            try {
                return UUID.fromString(deliveryId);
            } catch (IllegalArgumentException exception) {
                // Reported below with the same message as a missing header.
            }
        }
        throw new MalformedWebhookPayloadException("The X-GitHub-Delivery header must be a delivery GUID.");
    }

    private WebhookOutcome recordChange(VerifiedGitHubWebhook webhook, MergedPullRequest pullRequest, UUID deliveryId) {
        ChangeIntake.Outcome outcome = intake.record(
                webhook.organizationId(),
                webhook.projectId(),
                webhook.sourceId(),
                pullRequest,
                ChangeOrigin.WEBHOOK,
                deliveryId
        );
        if (outcome == ChangeIntake.Outcome.DUPLICATE) {
            return WebhookOutcome.DUPLICATE;
        }
        log.info(
                "Recorded merged pull request #{} for project {} from delivery {}.",
                pullRequest.number(),
                webhook.projectId(),
                deliveryId
        );
        return WebhookOutcome.RECORDED;
    }
}
