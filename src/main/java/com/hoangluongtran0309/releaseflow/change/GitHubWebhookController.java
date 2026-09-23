package com.hoangluongtran0309.releaseflow.change;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    GitHubWebhookController(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    // The body stays as raw bytes because the signature covers exactly what GitHub sent.
    @PostMapping(path = "/webhooks/github/{webhookId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    WebhookReceipt receive(
            @PathVariable String webhookId,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(name = "X-GitHub-Event", required = false) String event,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody(required = false) byte[] body
    ) {
        WebhookOutcome outcome = webhookService.receive(
                webhookId,
                signature,
                event,
                deliveryId,
                body == null ? new byte[0] : body
        );
        return new WebhookReceipt(outcome.value());
    }

    record WebhookReceipt(String outcome) {
    }
}
