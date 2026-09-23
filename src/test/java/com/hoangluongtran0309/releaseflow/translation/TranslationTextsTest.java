package com.hoangluongtran0309.releaseflow.translation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationTextsTest {

    @Test
    void theHashIgnoresKeyOrderButNotTextOrSourceLanguage() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("whatChanged", "Tables export as CSV.");
        first.put("whyChanged", "Users asked.");
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("whyChanged", "Users asked.");
        reordered.put("whatChanged", "Tables export as CSV.");

        String hash = TranslationTexts.inputHash("en", first);
        assertThat(hash).hasSize(64).isEqualTo(TranslationTexts.inputHash("en", reordered));
        assertThat(TranslationTexts.inputHash("de", first)).isNotEqualTo(hash);
        assertThat(TranslationTexts.inputHash("en", Map.of("whatChanged", "Tables export as TSV."))).isNotEqualTo(hash);
    }

    @Test
    void blankTextsAreNotTranslated() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("whatChanged", "Tables export as CSV.");
        texts.put("migrationStep", "");
        texts.put("narrative.operator", "  ");

        assertThat(TranslationTexts.nonBlank(texts)).containsOnlyKeys("whatChanged");
    }

    @Test
    void aRegionalVariantIsTheSameLanguage() {
        assertThat(TranslationTexts.sameLanguage("vi", "vi-VN")).isTrue();
        assertThat(TranslationTexts.sameLanguage("en-GB", "en")).isTrue();
        assertThat(TranslationTexts.sameLanguage("en", "vi")).isFalse();
    }

    @Test
    void backoffDoublesUpToFiveMinutes() {
        assertThat(TranslationWorker.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(TranslationWorker.backoff(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(TranslationWorker.backoff(20)).isEqualTo(Duration.ofSeconds(256));
    }

    @Test
    void theProviderIsDisabledUnlessDeepLIsChosenWithAKey() {
        ObjectMapper mapper = new ObjectMapper();
        Duration timeout = Duration.ofSeconds(5);

        assertThat(TranslationProviderConfiguration.create("disabled", "", "", timeout, mapper).isEnabled()).isFalse();
        assertThat(TranslationProviderConfiguration.create("", "", "", timeout, mapper).isEnabled()).isFalse();
        assertThat(TranslationProviderConfiguration.create("DeepL", "key", "", timeout, mapper).isEnabled()).isTrue();
        assertThatThrownBy(() -> TranslationProviderConfiguration.create("deepl", " ", "", timeout, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_DEEPL_API_KEY");
        assertThatThrownBy(() -> TranslationProviderConfiguration.create("disabled", "key", "", timeout, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_TRANSLATION_PROVIDER");
        assertThatThrownBy(() -> TranslationProviderConfiguration.create("google", "", "", timeout, mapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled or deepl");
    }
}
