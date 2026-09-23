package com.hoangluongtran0309.releaseflow.project;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public final class CredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom;

    @Autowired
    CredentialCipher(@Value("${releaseflow.credentials.master-key}") String encodedKey) {
        this(encodedKey, new SecureRandom());
    }

    CredentialCipher(String encodedKey, SecureRandom secureRandom) {
        this.key = new SecretKeySpec(decodeKey(encodedKey), "AES");
        this.secureRandom = secureRandom;
    }

    public EncryptedSecret encrypt(String plaintext, byte[] additionalAuthenticatedData) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Credential plaintext is required.");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(additionalAuthenticatedData);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(nonce, ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt credential.", exception);
        }
    }

    public String decrypt(EncryptedSecret encryptedSecret, byte[] additionalAuthenticatedData) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, encryptedSecret.nonce())
            );
            cipher.updateAAD(additionalAuthenticatedData);
            byte[] plaintext = cipher.doFinal(encryptedSecret.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt credential.", exception);
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("Credential master key must be configured as 32 Base64-encoded bytes.");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Credential master key must be valid Base64.");
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("Credential master key must decode to exactly 32 bytes.");
        }
        return decoded;
    }

    public record EncryptedSecret(byte[] nonce, byte[] ciphertext) {

        public EncryptedSecret {
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
    }
}
