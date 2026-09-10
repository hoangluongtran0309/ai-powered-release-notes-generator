package com.hoangluongtran0309.releaseflow.project;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    @Test
    void encryptsAndDecryptsWithRandomNonces() {
        CredentialCipher cipher = cipher();
        byte[] aad = "tenant/project/integration/repository".getBytes(StandardCharsets.UTF_8);

        CredentialCipher.EncryptedSecret first = cipher.encrypt("webhook-secret", aad);
        CredentialCipher.EncryptedSecret second = cipher.encrypt("webhook-secret", aad);

        assertThat(first.nonce()).hasSize(12).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(first, aad)).isEqualTo("webhook-secret");
        assertThat(cipher.decrypt(second, aad)).isEqualTo("webhook-secret");
    }

    @Test
    void rejectsTamperedCiphertextAndAuthenticatedData() {
        CredentialCipher cipher = cipher();
        byte[] aad = "original-context".getBytes(StandardCharsets.UTF_8);
        CredentialCipher.EncryptedSecret encrypted = cipher.encrypt("webhook-secret", aad);
        byte[] tamperedCiphertext = encrypted.ciphertext();
        tamperedCiphertext[0] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(
                new CredentialCipher.EncryptedSecret(encrypted.nonce(), tamperedCiphertext),
                aad
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not decrypt credential.");
        assertThatThrownBy(() -> cipher.decrypt(
                encrypted,
                "another-context".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not decrypt credential.");
    }

    @Test
    void failsFastForMissingMalformedOrWrongLengthKeysWithoutEchoingThem() {
        String malformed = "not-secret-key-material!";
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialCipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
        assertThatThrownBy(() -> new CredentialCipher(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid Base64")
                .hasMessageNotContaining(malformed);
        assertThatThrownBy(() -> new CredentialCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes")
                .hasMessageNotContaining(shortKey);
    }

    private static CredentialCipher cipher() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new CredentialCipher(Base64.getEncoder().encodeToString(key));
    }
}
