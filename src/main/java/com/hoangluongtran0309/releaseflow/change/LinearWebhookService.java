package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.LinearWebhookVerifier;
import com.hoangluongtran0309.releaseflow.project.VerifiedWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Turns a verified Linear delivery into a change. Only an issue that has just moved into
 * a completed state is one; everything else is acknowledged and ignored. The Organization
 * and Project come only from the source whose secret proved the delivery.
 */
@Service
class LinearWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LinearWebhookService.class);

    private final LinearWebhookVerifier verifier;
    private final ChangeIntake intake;
    private final WebhookBody webhookBody;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    LinearWebhookService(
            LinearWebhookVerifier verifier,
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

    WebhookOutcome receive(String webhookId, String signature, String deliveryId, byte[] body) {
        // Checked before the source is looked up, so the limit says nothing about what exists.
        webhookBody.requireWithinLimit(body);
        VerifiedWebhook webhook = verifier.verify(webhookId, signature, deliveryId, body)
                .orElseThrow(WebhookSignatureInvalidException::new);

        JsonNode payload = parse(body);
        if (!CompletedIssue.isCompletion(payload)) {
            verifier.recordDelivery(webhook, clock.instant());
            return WebhookOutcome.IGNORED;
        }
        requireConfiguredTeam(webhook, payload);

        WebhookOutcome outcome = recordChange(webhook, CompletedIssue.fromWebhook(payload));
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

    // A verified delivery still may not write into a team other than the connected one.
    private static void requireConfiguredTeam(VerifiedWebhook webhook, JsonNode payload) {
        String teamId = CompletedIssue.teamId(payload);
        if (teamId.isBlank() || !webhook.isProject(teamId)) {
            throw new WebhookRepositoryMismatchException();
        }
    }

    private WebhookOutcome recordChange(VerifiedWebhook webhook, MergedPullRequest issue) {
        // Linear names no delivery ReleaseFlow could record as a GUID.
        ChangeIntake.Outcome outcome = intake.record(
                webhook.organizationId(),
                webhook.projectId(),
                webhook.sourceId(),
                webhook.sourceType(),
                issue,
                ChangeOrigin.WEBHOOK,
                null
        );
        if (outcome == ChangeIntake.Outcome.DUPLICATE) {
            return WebhookOutcome.DUPLICATE;
        }
        log.info(
                "Recorded completed issue #{} for project {}.",
                issue.number(),
                webhook.projectId()
        );
        return WebhookOutcome.RECORDED;
    }
}
