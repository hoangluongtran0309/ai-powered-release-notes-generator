package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.GitLabWebhookVerifier;
import com.hoangluongtran0309.releaseflow.project.VerifiedWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

/**
 * Turns a verified GitLab delivery into a change. Nothing in the request is trusted until
 * the connected source's own secret proves it, and the Organization and Project then come
 * only from that source.
 */
@Service
class GitLabWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookService.class);
    private static final String MERGE_REQUEST = "merge_request";
    private static final String MERGED = "merge";

    private final GitLabWebhookVerifier verifier;
    private final ChangeIntake intake;
    private final WebhookBody webhookBody;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GitLabWebhookService(
            GitLabWebhookVerifier verifier,
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

    WebhookOutcome receive(String webhookId, Delivery delivery, byte[] body) {
        // Checked before the source is looked up, so the limit says nothing about what exists.
        webhookBody.requireWithinLimit(body);
        VerifiedWebhook webhook = verifier.verify(webhookId, delivery.headers(), body)
                .orElseThrow(WebhookSignatureInvalidException::new);

        JsonNode payload = parse(body);
        if (!MERGE_REQUEST.equals(payload.path("object_kind").asString(""))) {
            verifier.recordDelivery(webhook, clock.instant());
            return WebhookOutcome.IGNORED;
        }
        requireConfiguredProject(webhook, payload);

        JsonNode attributes = payload.path("object_attributes");
        WebhookOutcome outcome = MERGED.equals(attributes.path("action").asString(""))
                ? recordChange(webhook, MergedMergeRequest.fromWebhook(payload), delivery.eventId())
                : WebhookOutcome.IGNORED;
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

    // A signed delivery still may not write into a project other than the connected one.
    private static void requireConfiguredProject(VerifiedWebhook webhook, JsonNode payload) {
        JsonNode path = payload.path("project").path("path_with_namespace");
        if (!path.isString() || !webhook.isProject(path.stringValue())) {
            throw new WebhookRepositoryMismatchException();
        }
    }

    private WebhookOutcome recordChange(VerifiedWebhook webhook, MergedPullRequest mergeRequest, UUID deliveryId) {
        ChangeIntake.Outcome outcome = intake.record(
                webhook.organizationId(),
                webhook.projectId(),
                webhook.sourceId(),
                webhook.sourceType(),
                mergeRequest,
                ChangeOrigin.WEBHOOK,
                deliveryId
        );
        if (outcome == ChangeIntake.Outcome.DUPLICATE) {
            return WebhookOutcome.DUPLICATE;
        }
        log.info(
                "Recorded merged merge request !{} for project {} from delivery {}.",
                mergeRequest.number(),
                webhook.projectId(),
                deliveryId
        );
        return WebhookOutcome.RECORDED;
    }

    /**
     * The headers of one GitLab delivery. {@code eventUuid} is the only identifier GitLab
     * sends in both signature modes, and it is recorded when it is a UUID.
     */
    record Delivery(
            String deliveryId,
            String timestamp,
            String signature,
            String secretToken,
            String event,
            String eventUuid
    ) {

        GitLabWebhookVerifier.GitLabDeliveryHeaders headers() {
            return new GitLabWebhookVerifier.GitLabDeliveryHeaders(deliveryId, timestamp, signature, secretToken);
        }

        UUID eventId() {
            try {
                return eventUuid == null ? null : UUID.fromString(eventUuid);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
