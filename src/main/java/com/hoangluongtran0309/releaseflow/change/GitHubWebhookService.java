package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.GitHubWebhookVerifier;
import com.hoangluongtran0309.releaseflow.project.VerifiedWebhook;
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
    private final WebhookBody webhookBody;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GitHubWebhookService(
            GitHubWebhookVerifier verifier,
            ChangeIntake intake,
            WebhookBody webhookBody,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.verifier = verifier;
        this.intake = intake;
        this.webhookBody = webhookBody;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    WebhookOutcome receive(String webhookId, String signature, String event, String deliveryId, byte[] body) {
        // Checked before the source is looked up, so the limit says nothing about what exists.
        webhookBody.requireWithinLimit(body);
        // Nothing else from the request is trusted before this source's own secret verifies it.
        VerifiedWebhook webhook = verifier.verify(webhookId, signature, body)
                .orElseThrow(WebhookSignatureInvalidException::new);
        if (event == null || event.isBlank()) {
            throw new MalformedWebhookPayloadException("error.webhook_payload_malformed.githubEventHeader");
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
                throw new MalformedWebhookPayloadException("error.webhook_payload_malformed.notObject");
            }
            return payload;
        } catch (JacksonException exception) {
            throw new MalformedWebhookPayloadException("error.webhook_payload_malformed.notJson");
        }
    }

    private static void requireConfiguredRepository(VerifiedWebhook webhook, String event, JsonNode payload) {
        JsonNode fullName = payload.path("repository").path("full_name");
        if (fullName.isMissingNode() || fullName.isNull()) {
            // Organization-level pings carry no repository; a pull request always must.
            if ("pull_request".equals(event)) {
                throw new WebhookRepositoryMismatchException();
            }
            return;
        }
        if (!fullName.isString() || !webhook.isProject(fullName.stringValue())) {
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
        throw new MalformedWebhookPayloadException("error.webhook_payload_malformed.githubDeliveryHeader");
    }

    private WebhookOutcome recordChange(VerifiedWebhook webhook, MergedPullRequest pullRequest, UUID deliveryId) {
        ChangeIntake.Outcome outcome = intake.record(
                webhook.organizationId(),
                webhook.projectId(),
                webhook.sourceId(),
                webhook.sourceType(),
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
