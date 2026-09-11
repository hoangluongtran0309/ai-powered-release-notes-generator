package com.hoangluongtran0309.releaseflow.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
public class GitHubWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final int SIGNATURE_HEX_LENGTH = 64;

    private final GitHubIntegrationRepository integrationRepository;
    private final CredentialCipher credentialCipher;

    GitHubWebhookVerifier(GitHubIntegrationRepository integrationRepository, CredentialCipher credentialCipher) {
        this.integrationRepository = integrationRepository;
        this.credentialCipher = credentialCipher;
    }

    /**
     * Resolves the integration addressed by the untrusted path and returns it only
     * when its own secret produced the X-Hub-Signature-256 value for these exact bytes.
     */
    public Optional<VerifiedGitHubWebhook> verify(String webhookId, String signatureHeader, byte[] body) {
        Optional<byte[]> suppliedSignature = parseSignature(signatureHeader);
        Optional<UUID> parsedWebhookId = parseWebhookId(webhookId);
        if (suppliedSignature.isEmpty() || parsedWebhookId.isEmpty()) {
            return Optional.empty();
        }
        return integrationRepository.findByWebhookId(parsedWebhookId.get())
                .filter(integration -> signatureMatches(integration, body, suppliedSignature.get()))
                .map(integration -> new VerifiedGitHubWebhook(
                        integration.getOrganizationId(),
                        integration.getProjectId(),
                        integration.getId(),
                        integration.getRepositoryOwner(),
                        integration.getRepositoryName()
                ));
    }

    @Transactional
    public void recordDelivery(VerifiedGitHubWebhook webhook, Instant deliveredAt) {
        integrationRepository.recordDelivery(webhook.integrationId(), webhook.organizationId(), deliveredAt);
    }

    private boolean signatureMatches(GitHubIntegration integration, byte[] body, byte[] suppliedSignature) {
        final String secret;
        try {
            secret = credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(
                            integration.getSecretNonce(),
                            integration.getSecretCiphertext()
                    ),
                    GitHubIntegrationService.additionalAuthenticatedData(
                            integration.getOrganizationId(),
                            integration.getProjectId(),
                            integration.getId(),
                            integration.getRepositoryOwner(),
                            integration.getRepositoryName()
                    )
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the webhook secret of GitHub integration {}.", integration.getId());
            return false;
        }
        return MessageDigest.isEqual(hmacSha256(secret, body), suppliedSignature);
    }

    private static byte[] hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not compute webhook signature.", exception);
        }
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

    private static Optional<UUID> parseWebhookId(String webhookId) {
        try {
            return Optional.of(UUID.fromString(webhookId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
