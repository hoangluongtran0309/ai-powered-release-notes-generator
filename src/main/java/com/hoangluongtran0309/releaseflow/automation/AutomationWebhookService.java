package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.change.MalformedWebhookPayloadException;
import com.hoangluongtran0309.releaseflow.change.WebhookPayloadTooLargeException;
import com.hoangluongtran0309.releaseflow.change.WebhookSignatureInvalidException;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lets another system set a Rule off. The call carries a moment, a delivery, and a
 * signature over the raw body; only the Rule's own secret proves it. Everything that
 * follows comes from the Rule: the Organization, the Project it may deliver for, and
 * the Actions. The body says which release, never whose.
 *
 * <p>A repeated delivery is the same Run rather than a second set of deliveries, so a
 * caller that retries after a timeout costs nothing.
 */
@Service
class AutomationWebhookService {

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRunFactory runFactory;
    private final AutomationSecrets secrets;
    private final ReleaseAccess releaseAccess;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration allowedSkew;
    private final int maxBodyBytes;

    AutomationWebhookService(
            AutomationRuleRepository ruleRepository,
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationRunFactory runFactory,
            AutomationSecrets secrets,
            ReleaseAccess releaseAccess,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${releaseflow.automation.webhook-clock-skew}") Duration allowedSkew,
            @Value("${releaseflow.automation.webhook-max-body-bytes}") int maxBodyBytes
    ) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.runFactory = runFactory;
        this.secrets = secrets;
        this.releaseAccess = releaseAccess;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.allowedSkew = allowedSkew;
        this.maxBodyBytes = maxBodyBytes;
    }

    @Transactional
    RunStatus trigger(SignedRequest request) {
        AutomationRule rule = verify(request);
        UUID releaseId = releaseId(request.body());
        return status(runRepository.findByRuleIdAndRequestId(rule.getId(), request.deliveryId())
                .orElseGet(() -> create(rule, releaseId, request.deliveryId())));
    }

    /** The Run this webhook made, for a caller asking what became of its delivery. */
    @Transactional(readOnly = true)
    RunStatus status(UUID runId, SignedRequest request) {
        AutomationRule rule = verify(request);
        return status(runRepository.findByIdAndOrganizationId(runId, rule.getOrganizationId())
                .filter(run -> run.getRuleId().equals(rule.getId()))
                .orElseThrow(AutomationRunNotFoundException::new));
    }

    private RunStatus status(AutomationRun run) {
        return new RunStatus(
                run.getId(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getCompletedAt(),
                actionRunRepository.findAllByRunIdOrderByPositionAsc(run.getId()).stream()
                        .map(action -> new ActionStatus(
                                action.getPosition(),
                                action.getActionType(),
                                action.getStatus(),
                                action.getErrorCode()
                        ))
                        .toList()
        );
    }

    private AutomationRun create(AutomationRule rule, UUID releaseId, UUID deliveryId) {
        ReleaseAccess.ReleaseSnapshot release = releaseAccess.find(rule.getOrganizationId(), releaseId)
                .orElseThrow(AutomationConflictException::releaseNotPublished);
        if (release.status() != ReleaseStatus.PUBLISHED) {
            throw AutomationConflictException.releaseNotPublished();
        }
        if (!rule.matches(release.projectId(), TriggerType.EXTERNAL_WEBHOOK)) {
            throw AutomationConflictException.ruleUnavailable();
        }
        return runFactory.create(rule, release, TriggerType.EXTERNAL_WEBHOOK, deliveryId, null, null, null);
    }

    /**
     * Proves the call, in this order: a body small enough to sign at all, a moment close
     * enough to now, a Rule that still answers this path, and a signature its secret
     * makes. A caller learns nothing from which of them failed.
     */
    private AutomationRule verify(SignedRequest request) {
        if (request.body().length > maxBodyBytes) {
            throw new WebhookPayloadTooLargeException(maxBodyBytes);
        }
        Instant sentAt = sentAt(request.timestamp());
        if (sentAt == null || request.deliveryId() == null || request.signature() == null
                || Duration.between(sentAt, clock.instant()).abs().compareTo(allowedSkew) > 0) {
            throw new WebhookSignatureInvalidException();
        }
        AutomationRule rule = Optional.ofNullable(request.webhookId())
                .flatMap(ruleRepository::findByWebhookId)
                .filter(candidate -> candidate.getTriggerType() == TriggerType.EXTERNAL_WEBHOOK)
                .orElseThrow(WebhookSignatureInvalidException::new);
        String secret = secrets.decryptWebhookSecret(
                rule.getWebhookSecret(), rule.getOrganizationId(), rule.getId(), rule.getWebhookId());
        if (secret == null) {
            throw new WebhookSignatureInvalidException();
        }
        String expected = AutomationWebhookSignature.of(
                secret,
                request.timestamp(),
                request.deliveryId().toString(),
                request.method(),
                request.path(),
                request.body()
        );
        if (!AutomationWebhookSignature.matches(expected, request.signature())) {
            throw new WebhookSignatureInvalidException();
        }
        // A rule that was turned off answers nothing, but only a caller that could sign
        // learns that much.
        if (!rule.isEnabled() || !rule.isActive()) {
            throw new WebhookSignatureInvalidException();
        }
        return rule;
    }

    private UUID releaseId(byte[] body) {
        final JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JacksonException malformed) {
            throw new MalformedWebhookPayloadException("The webhook body must be a JSON object with a releaseId.");
        }
        if (payload == null || !payload.isObject() || !payload.path("releaseId").isString()) {
            throw new MalformedWebhookPayloadException("The webhook body must be a JSON object with a releaseId.");
        }
        try {
            return UUID.fromString(payload.path("releaseId").stringValue());
        } catch (IllegalArgumentException notAnId) {
            throw new MalformedWebhookPayloadException("The webhook body must be a JSON object with a releaseId.");
        }
    }

    // Seconds since the epoch, as the signature covers them; anything else is no moment.
    private static Instant sentAt(String timestamp) {
        if (timestamp == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp.strip()));
        } catch (NumberFormatException | ArithmeticException unreadable) {
            return null;
        }
    }

    /** What a caller is told about its own Run: no note, no configuration, no secret. */
    record RunStatus(
            UUID runId,
            ExecutionStatus status,
            Instant createdAt,
            Instant completedAt,
            List<ActionStatus> actions
    ) {

        RunStatus {
            actions = List.copyOf(actions);
        }
    }

    record ActionStatus(int position, ActionType actionType, ExecutionStatus status, String errorCode) {
    }

    /** One signed call, exactly as it arrived. */
    record SignedRequest(
            UUID webhookId,
            String method,
            String path,
            String timestamp,
            UUID deliveryId,
            String signature,
            byte[] body
    ) {

        SignedRequest {
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
