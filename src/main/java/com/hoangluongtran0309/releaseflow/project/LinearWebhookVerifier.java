package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies a Linear delivery with the connected source's own secret: a hex HMAC-SHA256 of
 * the raw body in {@code Linear-Signature}, a {@code Linear-Delivery} header, a payload
 * signed recently, and the workspace this source belongs to. Unlike the other verifiers
 * this one reads the body, because Linear puts the timestamp and the workspace inside it
 * rather than in headers; an unreadable body is simply one more way to fail.
 */
@Component
public class LinearWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(LinearWebhookVerifier.class);
    // Any real epoch-millisecond value is larger than this; any epoch-second value is not.
    private static final long MILLISECOND_THRESHOLD = 10_000_000_000L;

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration clockSkew;

    LinearWebhookVerifier(
            IntegrationSourceRepository sourceRepository,
            CredentialCipher credentialCipher,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${releaseflow.linear.webhook-clock-skew}") Duration clockSkew
    ) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.clockSkew = clockSkew;
    }

    public Optional<VerifiedWebhook> verify(String webhookId, String signature, String deliveryId, byte[] body) {
        Optional<UUID> parsedWebhookId = WebhookSources.parseWebhookId(webhookId);
        if (parsedWebhookId.isEmpty() || signature == null || deliveryId == null) {
            return Optional.empty();
        }
        return sourceRepository.findByWebhookId(parsedWebhookId.get())
                .filter(source -> source.getSourceType() == SourceType.LINEAR)
                .filter(source -> proves(source, signature, body))
                .map(WebhookSources::verified);
    }

    @Transactional
    public void recordDelivery(VerifiedWebhook webhook, Instant deliveredAt) {
        sourceRepository.recordDelivery(webhook.sourceId(), webhook.organizationId(), deliveredAt);
    }

    private boolean proves(IntegrationSource source, String signature, byte[] body) {
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
        final JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (JacksonException exception) {
            return false;
        }
        if (payload == null || !payload.isObject()) {
            return false;
        }
        return fresh(payload.path("createdAt"))
                && workspaceMatches(source, payload.path("organizationId"))
                && signatureMatches(secret, signature, body);
    }

    private boolean signatureMatches(String secret, String signature, byte[] body) {
        byte[] expected = WebhookSignatures.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), body);
        return MessageDigest.isEqual(
                HexFormat.of().formatHex(expected).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    // The delivery is only for the workspace this source was connected to.
    private static boolean workspaceMatches(IntegrationSource source, JsonNode organizationId) {
        return organizationId.isString() && organizationId.stringValue().equals(source.getExternalWorkspaceKey());
    }

    // A replayed delivery is refused once the time Linear stamped on it is too old.
    private boolean fresh(JsonNode createdAt) {
        Instant signedAt = instant(createdAt);
        if (signedAt == null) {
            return false;
        }
        return Math.abs(clock.instant().getEpochSecond() - signedAt.getEpochSecond()) <= clockSkew.toSeconds();
    }

    private static Instant instant(JsonNode createdAt) {
        if (createdAt.isNumber()) {
            long raw = createdAt.longValue();
            return raw > MILLISECOND_THRESHOLD ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        if (!createdAt.isString()) {
            return null;
        }
        try {
            return Instant.parse(createdAt.stringValue());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
