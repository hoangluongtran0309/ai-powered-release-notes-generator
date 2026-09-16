package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
public class GitHubWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookVerifier.class);
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int SIGNATURE_HEX_LENGTH = 64;

    private final IntegrationSourceRepository sourceRepository;
    private final CredentialCipher credentialCipher;

    GitHubWebhookVerifier(IntegrationSourceRepository sourceRepository, CredentialCipher credentialCipher) {
        this.sourceRepository = sourceRepository;
        this.credentialCipher = credentialCipher;
    }

    /**
     * Resolves the source addressed by the untrusted path and returns it only
     * when its own secret produced the X-Hub-Signature-256 value for these exact bytes.
     */
    public Optional<VerifiedWebhook> verify(String webhookId, String signatureHeader, byte[] body) {
        Optional<byte[]> suppliedSignature = parseSignature(signatureHeader);
        Optional<UUID> parsedWebhookId = WebhookSources.parseWebhookId(webhookId);
        if (suppliedSignature.isEmpty() || parsedWebhookId.isEmpty()) {
            return Optional.empty();
        }
        return sourceRepository.findByWebhookId(parsedWebhookId.get())
                .filter(source -> source.getSourceType() == SourceType.GITHUB)
                .filter(source -> signatureMatches(source, body, suppliedSignature.get()))
                .map(WebhookSources::verified);
    }

    @Transactional
    public void recordDelivery(VerifiedWebhook webhook, Instant deliveredAt) {
        sourceRepository.recordDelivery(webhook.sourceId(), webhook.organizationId(), deliveredAt);
    }

    private boolean signatureMatches(IntegrationSource source, byte[] body, byte[] suppliedSignature) {
        final String secret;
        try {
            secret = credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(
                            source.getSecretNonce(),
                            source.getSecretCiphertext()
                    ),
                    IntegrationSourceService.secretAuthenticatedData(source)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the webhook secret of source {}.", source.getId());
            return false;
        }
        byte[] expected = WebhookSignatures.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), body);
        return MessageDigest.isEqual(expected, suppliedSignature);
    }

    private static Optional<byte[]> parseSignature(String signatureHeader) {
        if (signatureHeader == null
                || !signatureHeader.startsWith(SIGNATURE_PREFIX)
                || signatureHeader.length() != SIGNATURE_PREFIX.length() + SIGNATURE_HEX_LENGTH) {
            return Optional.empty();
        }
        try {
            return Optional.of(HexFormat.of().parseHex(signatureHeader, SIGNATURE_PREFIX.length(), signatureHeader.length()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
