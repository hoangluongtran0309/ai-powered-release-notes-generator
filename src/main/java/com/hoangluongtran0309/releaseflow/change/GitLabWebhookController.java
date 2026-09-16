package com.hoangluongtran0309.releaseflow.change;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitLabWebhookController {

    private final GitLabWebhookService webhookService;

    GitLabWebhookController(GitLabWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    // The body stays as raw bytes because a signature covers exactly what GitLab sent.
    @PostMapping(path = "/webhooks/gitlab/{webhookId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    WebhookReceipt receive(
            @PathVariable String webhookId,
            @RequestHeader(name = "webhook-id", required = false) String deliveryId,
            @RequestHeader(name = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(name = "webhook-signature", required = false) String signature,
            @RequestHeader(name = "X-Gitlab-Token", required = false) String secretToken,
            @RequestHeader(name = "X-Gitlab-Event", required = false) String event,
            @RequestHeader(name = "X-Gitlab-Event-UUID", required = false) String eventUuid,
            @RequestBody(required = false) byte[] body
    ) {
        WebhookOutcome outcome = webhookService.receive(
                webhookId,
                new GitLabWebhookService.Delivery(deliveryId, timestamp, signature, secretToken, event, eventUuid),
                body == null ? new byte[0] : body
        );
        return new WebhookReceipt(outcome.value());
    }

    record WebhookReceipt(String outcome) {
    }
}
