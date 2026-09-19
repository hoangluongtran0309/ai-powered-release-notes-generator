package com.hoangluongtran0309.releaseflow.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DeepL's {@code POST /v2/translate}. Texts are sent in batches that stay under DeepL's
 * request limits, and a response that does not return one translation per text is
 * rejected. The API key is never logged.
 */
final class DeepLTranslator implements Translator {

    static final int MAX_BATCH_BYTES = 120 * 1024;
    static final int MAX_BATCH_TEXTS = 50;
    static final String FREE_BASE_URL = "https://api-free.deepl.com";
    static final String PRO_BASE_URL = "https://api.deepl.com";

    private static final Logger log = LoggerFactory.getLogger(DeepLTranslator.class);
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int QUOTA_EXCEEDED = 456;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    DeepLTranslator(String apiKey, String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // A redirect proves nothing about who answered, so it is never followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl(apiKey) : baseUrl.strip())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    // DeepL issues keys ending in ":fx" for its free API, which has its own host.
    static String defaultBaseUrl(String apiKey) {
        return apiKey.endsWith(":fx") ? FREE_BASE_URL : PRO_BASE_URL;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage)
            throws TranslationException {
        Translator.requireNoTransaction();
        List<String> translations = new ArrayList<>(texts.size());
        for (List<String> batch : batches(texts)) {
            translations.addAll(translateBatch(batch, sourceCode(sourceLanguage), targetCode(targetLanguage)));
        }
        return translations;
    }

    /** Consecutive texts, at most {@value #MAX_BATCH_TEXTS} and about 120 KiB per request. */
    static List<List<String>> batches(List<String> texts) {
        List<List<String>> batches = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int bytes = 0;
        for (String text : texts) {
            int size = text.getBytes(StandardCharsets.UTF_8).length;
            if (!current.isEmpty() && (current.size() == MAX_BATCH_TEXTS || bytes + size > MAX_BATCH_BYTES)) {
                batches.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(text);
            bytes += size;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    // DeepL takes a source language without region.
    static String sourceCode(String languageTag) {
        return Locale.forLanguageTag(languageTag).getLanguage().toUpperCase(Locale.ROOT);
    }

    // DeepL needs a regional variant for English and Portuguese targets.
    static String targetCode(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage().toUpperCase(Locale.ROOT);
        String region = locale.getCountry();
        if (!region.isEmpty()) {
            return language + "-" + region.toUpperCase(Locale.ROOT);
        }
        return switch (language) {
            case "EN" -> "EN-US";
            case "PT" -> "PT-BR";
            default -> language;
        };
    }

    private List<String> translateBatch(List<String> batch, String source, String target) throws TranslationException {
        ObjectNode body = objectMapper.createObjectNode();
        batch.forEach(body.putArray("text")::add);
        body.put("source_lang", source);
        body.put("target_lang", target);
        String response;
        try {
            response = restClient.post()
                    .uri("/v2/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("DeepL returned HTTP {}.", status);
            if (status == TOO_MANY_REQUESTS) {
                throw new TranslationException(TranslationException.RATE_LIMITED, true);
            }
            if (status == QUOTA_EXCEEDED) {
                throw new TranslationException(TranslationException.QUOTA_EXCEEDED, false);
            }
            throw exception.getStatusCode().is5xxServerError()
                    ? new TranslationException(TranslationException.UNAVAILABLE, true)
                    : new TranslationException(TranslationException.REJECTED, false);
        } catch (RestClientException exception) {
            log.warn("Could not reach DeepL.");
            throw new TranslationException(TranslationException.UNAVAILABLE, true);
        }
        return parse(response, batch.size());
    }

    private List<String> parse(String response, int expected) throws TranslationException {
        final JsonNode translations;
        try {
            translations = objectMapper.readTree(response == null ? "" : response).path("translations");
        } catch (JacksonException exception) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        if (!translations.isArray() || translations.size() != expected) {
            throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
        }
        List<String> texts = new ArrayList<>(expected);
        for (JsonNode translation : translations.values()) {
            JsonNode text = translation.path("text");
            if (!text.isString()) {
                throw new TranslationException(TranslationException.INVALID_RESPONSE, false);
            }
            texts.add(text.stringValue());
        }
        return texts;
    }
}
