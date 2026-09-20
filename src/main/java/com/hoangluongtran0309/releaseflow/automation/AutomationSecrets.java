package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.project.CredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Encrypts an Action's secret and decrypts it again only for the delivery itself. The
 * ciphertext is bound to the Organization, the Rule, the Action, and the Action's
 * kind, so a secret cannot be moved to another Action or read as a webhook secret.
 * Rewriting any of those columns stops the secret decrypting, which is deliberate.
 */
@Component
public class AutomationSecrets {

    private static final String PURPOSE = "automation-action-secret";
    private static final String WEBHOOK_PURPOSE = "automation-webhook-secret";
    private static final Logger log = LoggerFactory.getLogger(AutomationSecrets.class);

    private final CredentialCipher credentialCipher;

    AutomationSecrets(CredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    Secret encrypt(String rawSecret, UUID organizationId, UUID ruleId, UUID actionId, ActionType actionType) {
        CredentialCipher.EncryptedSecret encrypted = credentialCipher.encrypt(
                rawSecret,
                authenticatedData(organizationId, ruleId, actionId, actionType)
        );
        return new Secret(encrypted.nonce(), encrypted.ciphertext());
    }

    /** A secret that no longer decrypts is treated as missing, which fails the Action. */
    String decrypt(Secret secret, UUID organizationId, UUID ruleId, UUID actionId, ActionType actionType) {
        if (secret == null) {
            return null;
        }
        try {
            return credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(secret.nonce(), secret.ciphertext()),
                    authenticatedData(organizationId, ruleId, actionId, actionType)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the secret of automation Action {}.", actionId);
            return null;
        }
    }

    /**
     * The secret that proves a call to a Rule's own webhook. It is bound to the Rule
     * and to the path callers use, so it can never be read as an Action's secret, and
     * rotating it keeps both.
     */
    Secret encryptWebhookSecret(String rawSecret, UUID organizationId, UUID ruleId, UUID webhookId) {
        CredentialCipher.EncryptedSecret encrypted = credentialCipher.encrypt(
                rawSecret,
                webhookAuthenticatedData(organizationId, ruleId, webhookId)
        );
        return new Secret(encrypted.nonce(), encrypted.ciphertext());
    }

    /** A webhook secret that no longer decrypts is treated as missing: no call proves. */
    String decryptWebhookSecret(Secret secret, UUID organizationId, UUID ruleId, UUID webhookId) {
        if (secret == null) {
            return null;
        }
        try {
            return credentialCipher.decrypt(
                    new CredentialCipher.EncryptedSecret(secret.nonce(), secret.ciphertext()),
                    webhookAuthenticatedData(organizationId, ruleId, webhookId)
            );
        } catch (IllegalStateException exception) {
            log.warn("Could not decrypt the webhook secret of automation Rule {}.", ruleId);
            return null;
        }
    }

    private static byte[] webhookAuthenticatedData(UUID organizationId, UUID ruleId, UUID webhookId) {
        return String.join(
                "\n",
                organizationId.toString(),
                ruleId.toString(),
                webhookId.toString(),
                WEBHOOK_PURPOSE
        ).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] authenticatedData(UUID organizationId, UUID ruleId, UUID actionId, ActionType actionType) {
        return String.join(
                "\n",
                organizationId.toString(),
                ruleId.toString(),
                actionId.toString(),
                actionType.name(),
                PURPOSE
        ).getBytes(StandardCharsets.UTF_8);
    }

    /** An encrypted Action secret. Never log, serialize, or return this. */
    public record Secret(byte[] nonce, byte[] ciphertext) {

        public Secret {
            nonce = nonce.clone();
            ciphertext = ciphertext.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }

        @Override
        public String toString() {
            return "Secret[***]";
        }
    }
}
