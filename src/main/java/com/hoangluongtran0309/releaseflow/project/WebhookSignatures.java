package com.hoangluongtran0309.releaseflow.project;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * The signature arithmetic the providers' webhooks share. Every comparison is
 * constant-time, and a value that cannot be parsed never matches.
 */
final class WebhookSignatures {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String STANDARD_WEBHOOK_SECRET_PREFIX = "whsec_";
    private static final String STANDARD_WEBHOOK_VERSION = "v1,";

    private WebhookSignatures() {
    }

    static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not compute webhook signature.", exception);
        }
    }

    static boolean equal(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Standard Webhooks: the secret signs {@code id.timestamp.body}, and the header holds
     * one or more space-separated candidates so a secret can be rotated.
     */
    static boolean standardWebhook(byte[] body, String deliveryId, String timestamp, String suppliedHeader, String secret) {
        if (deliveryId == null || timestamp == null || suppliedHeader == null) {
            return false;
        }
        final byte[] key;
        try {
            key = decodeSecret(secret);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        byte[] signed = signedPayload(deliveryId, timestamp, body);
        String expected = STANDARD_WEBHOOK_VERSION + Base64.getEncoder().encodeToString(hmacSha256(key, signed));
        boolean matched = false;
        for (String candidate : suppliedHeader.split(" ")) {
            // Every candidate is compared, so the answer does not depend on which one matched.
            matched |= equal(expected, candidate.strip());
        }
        return matched;
    }

    private static byte[] signedPayload(String deliveryId, String timestamp, byte[] body) {
        byte[] prefix = (deliveryId + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        return signed;
    }

    // A Standard Webhooks secret is Base64, optionally behind a whsec_ label.
    private static byte[] decodeSecret(String secret) {
        String encoded = secret.startsWith(STANDARD_WEBHOOK_SECRET_PREFIX)
                ? secret.substring(STANDARD_WEBHOOK_SECRET_PREFIX.length())
                : secret;
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException standardAlphabetFailed) {
            return Base64.getUrlDecoder().decode(encoded);
        }
    }
}
