package com.hoangluongtran0309.releaseflow.translation;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Hashes and normalizes the texts of one translation. */
final class TranslationTexts {

    private static final ObjectMapper CANONICAL = new ObjectMapper();

    private TranslationTexts() {
    }

    /** The texts worth translating: blank values are left out, keys keep their order. */
    static Map<String, String> nonBlank(Map<String, String> texts) {
        Map<String, String> kept = new LinkedHashMap<>();
        texts.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                kept.put(key, value);
            }
        });
        return kept;
    }

    /** SHA-256 of the source language and the texts sorted by key, so equal input hashes equally. */
    static String inputHash(String sourceLanguage, Map<String, String> texts) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("source", sourceLanguage);
        canonical.put("texts", new TreeMap<>(texts));
        return sha256(CANONICAL.writeValueAsString(canonical));
    }

    static String textHash(String text) {
        return sha256(text);
    }

    /** Two tags with the same primary language, such as {@code vi} and {@code vi-VN}, need no translation. */
    static boolean sameLanguage(String first, String second) {
        return Locale.forLanguageTag(first).getLanguage().equals(Locale.forLanguageTag(second).getLanguage());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
