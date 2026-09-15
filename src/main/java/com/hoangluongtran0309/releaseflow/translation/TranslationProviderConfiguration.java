package com.hoangluongtran0309.releaseflow.translation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Locale;

/**
 * Builds the translation provider named by {@code RELEASEFLOW_TRANSLATION_PROVIDER}:
 * {@code disabled} (the default) or {@code deepl}, which needs its API key. A DeepL key
 * without the provider stops startup, so translation is never switched off silently.
 */
@Configuration(proxyBeanMethods = false)
class TranslationProviderConfiguration {

    static final String DISABLED = "disabled";
    static final String DEEPL = "deepl";

    @Bean
    TranslationProvider translationProvider(
            @Value("${releaseflow.translation.provider}") String provider,
            @Value("${releaseflow.deepl.api-key}") String apiKey,
            @Value("${releaseflow.deepl.base-url}") String baseUrl,
            @Value("${releaseflow.translation.timeout}") Duration timeout,
            ObjectMapper objectMapper
    ) {
        return create(provider, apiKey, baseUrl, timeout, objectMapper);
    }

    static TranslationProvider create(
            String providerValue,
            String apiKeyValue,
            String baseUrl,
            Duration timeout,
            ObjectMapper objectMapper
    ) {
        String provider = providerValue == null ? "" : providerValue.strip().toLowerCase(Locale.ROOT);
        String apiKey = apiKeyValue == null ? "" : apiKeyValue.strip();
        if (provider.isEmpty() || provider.equals(DISABLED)) {
            if (!apiKey.isEmpty()) {
                throw new IllegalStateException(
                        "RELEASEFLOW_DEEPL_API_KEY is set, but RELEASEFLOW_TRANSLATION_PROVIDER is not deepl. "
                                + "Set RELEASEFLOW_TRANSLATION_PROVIDER=deepl to translate release notes, or remove the key."
                );
            }
            return new TranslationProvider(null);
        }
        if (!provider.equals(DEEPL)) {
            throw new IllegalStateException("RELEASEFLOW_TRANSLATION_PROVIDER must be disabled or deepl.");
        }
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("RELEASEFLOW_TRANSLATION_PROVIDER=deepl needs RELEASEFLOW_DEEPL_API_KEY.");
        }
        return new TranslationProvider(new DeepLTranslator(apiKey, baseUrl, timeout, objectMapper));
    }
}
