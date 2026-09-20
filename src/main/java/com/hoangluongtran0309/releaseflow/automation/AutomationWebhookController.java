package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.change.WebhookSignatureInvalidException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The path another system calls to set a Rule off, and the path it asks what happened.
 * Both are signed; neither is authenticated, because the signature is the identity.
 * The body is kept as raw bytes, because that is exactly what the signature covers.
 */
@RestController
@RequestMapping("/webhooks/automation/{webhookId}")
public class AutomationWebhookController {

    private static final String TIMESTAMP = "X-ReleaseFlow-Timestamp";
    private static final String DELIVERY = "X-ReleaseFlow-Delivery";
    private static final String SIGNATURE = "X-ReleaseFlow-Signature-256";

    private final AutomationWebhookService webhookService;

    AutomationWebhookController(AutomationWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TriggerReceipt> trigger(
            @PathVariable String webhookId,
            @RequestHeader(name = TIMESTAMP, required = false) String timestamp,
            @RequestHeader(name = DELIVERY, required = false) String deliveryId,
            @RequestHeader(name = SIGNATURE, required = false) String signature,
            @RequestBody(required = false) byte[] body,
            HttpServletRequest servletRequest
    ) {
        AutomationWebhookService.RunStatus run = webhookService.trigger(
                signed(webhookId, timestamp, deliveryId, signature, body, servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(new TriggerReceipt(
                        run.runId(),
                        run.status(),
                        "/webhooks/automation/" + webhookId + "/runs/" + run.runId()
                ));
    }

    @GetMapping("/runs/{runId}")
    ResponseEntity<RunReceipt> status(
            @PathVariable String webhookId,
            @PathVariable UUID runId,
            @RequestHeader(name = TIMESTAMP, required = false) String timestamp,
            @RequestHeader(name = DELIVERY, required = false) String deliveryId,
            @RequestHeader(name = SIGNATURE, required = false) String signature,
            HttpServletRequest servletRequest
    ) {
        AutomationWebhookService.RunStatus run = webhookService.status(
                runId, signed(webhookId, timestamp, deliveryId, signature, new byte[0], servletRequest));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new RunReceipt(
                        run.runId(),
                        run.status(),
                        run.createdAt(),
                        run.completedAt(),
                        run.actions().stream()
                                .map(action -> new ActionReceipt(
                                        action.position(),
                                        action.actionType(),
                                        action.status(),
                                        action.errorCode()
                                ))
                                .toList()
                ));
    }

    private static AutomationWebhookService.SignedRequest signed(
            String webhookId,
            String timestamp,
            String deliveryId,
            String signature,
            byte[] body,
            HttpServletRequest servletRequest
    ) {
        return new AutomationWebhookService.SignedRequest(
                identifier(webhookId),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                timestamp,
                identifier(deliveryId),
                signature,
                body
        );
    }

    // A path or a delivery that is not an identifier at all fails as any other
    // unprovable call does, and says nothing about what exists.
    private static UUID identifier(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnIdentifier) {
            throw new WebhookSignatureInvalidException();
        }
    }

    record TriggerReceipt(UUID runId, ExecutionStatus status, String statusPath) {
    }

    record RunReceipt(
            UUID runId,
            ExecutionStatus status,
            Instant createdAt,
            Instant completedAt,
            List<ActionReceipt> actions
    ) {
    }

    record ActionReceipt(int position, ActionType actionType, ExecutionStatus status, String errorCode) {
    }
}
