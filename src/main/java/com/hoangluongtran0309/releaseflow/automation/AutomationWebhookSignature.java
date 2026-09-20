package com.hoangluongtran0309.releaseflow.automation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The arithmetic that proves a call to a Rule's own webhook. The secret signs the
 * moment, the delivery, the method, the path, and a digest of the body, each on its own
 * line, so a signature cannot be replayed against another route or another request.
 * Every comparison is constant-time, and anything that cannot be read never matches.
 */
final class AutomationWebhookSignature {

    static final String PREFIX = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private AutomationWebhookSignature() {
    }

    static String of(String secret, String timestamp, String deliveryId, String method, String path, byte[] body) {
        String canonical = String.join(
                "\n",
                timestamp,
                deliveryId,
                method.toUpperCase(Locale.ROOT),
                path,
                sha256Hex(body)
        );
        return PREFIX + HexFormat.of().formatHex(hmacSha256(
                secret.getBytes(StandardCharsets.UTF_8),
                canonical.getBytes(StandardCharsets.UTF_8)
        ));
    }

    static boolean matches(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.strip().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not digest a webhook body.", exception);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not compute a webhook signature.", exception);
        }
    }
}
