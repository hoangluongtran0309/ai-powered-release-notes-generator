package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies a GitLab delivery with the connected source's own secret, in whichever way that
 * source was set up. An unknown webhook ID, a source of another type, a secret that cannot
 * be decrypted, a stale timestamp, and a wrong signature or token are all the same answer.
 */
@Component
public class GitLabWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitLabWebhookVerifier.class);

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final Clock clock;
    private final Duration clockSkew;

    GitLabWebhookVerifier(
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            Clock clock,
            @Value("${releaseflow.gitlab.webhook-clock-skew}") Duration clockSkew
    ) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.clock = clock;
        this.clockSkew = clockSkew;
    }

    public Optional<VerifiedWebhook> verify(String webhookId, GitLabDeliveryHeaders headers, byte[] body) {
        Optional<UUID> parsedWebhookId = WebhookSources.parseWebhookId(webhookId);
        if (parsedWebhookId.isEmpty()) {
            return Optional.empty();
        }
        return sourceRepository.findByWebhookId(parsedWebhookId.get())
                .filter(source -> source.getSourceType() == SourceType.GITLAB)
                .filter(source -> proves(source, headers, body))
                .map(WebhookSources::verified);
    }

    @Transactional
    public void recordDelivery(VerifiedWebhook webhook, Instant deliveredAt) {
        sourceRepository.recordDelivery(webhook.sourceId(), webhook.organizationId(), deliveredAt);
    }

    private boolean proves(IntegrationSource source, GitLabDeliveryHeaders headers, byte[] body) {
        final String secret;
        try {
            secret = credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(source.getSecretNonce(), source.getSecretCiphertext()),
                    IntegrationSourceService.secretAuthenticatedData(source)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the webhook secret of source {}.", source.getId());
            return false;
        }
        return switch (source.getWebhookAuthMode()) {
            case GITLAB_SECRET_TOKEN -> WebhookSignatures.equal(secret, headers.secretToken());
            case GITLAB_SIGNING_TOKEN -> fresh(headers.timestamp())
                    && WebhookSignatures.standardWebhook(
                            body,
                            headers.deliveryId(),
                            headers.timestamp(),
                            headers.signature(),
                            secret
                    );
            // A GitLab source is never set up with another provider's scheme.
            case GITHUB_HMAC, LINEAR_HMAC -> false;
        };
    }

    // A replayed delivery is refused once its signed timestamp is outside the allowed skew.
    private boolean fresh(String timestamp) {
        if (timestamp == null) {
            return false;
        }
        try {
            long signedAt = Long.parseLong(timestamp.strip());
            long now = clock.instant().getEpochSecond();
            return Math.abs(now - signedAt) <= clockSkew.toSeconds();
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * The headers a GitLab delivery may carry to prove itself: the Standard Webhooks trio,
     * or the secret token repeated verbatim.
     */
    public record GitLabDeliveryHeaders(String deliveryId, String timestamp, String signature, String secretToken) {
    }
}
