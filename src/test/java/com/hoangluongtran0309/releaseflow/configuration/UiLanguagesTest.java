package com.hoangluongtran0309.releaseflow.configuration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiLanguagesTest {

    @Test
    void narrowsARegionToTheLanguageThisDeploymentShips() {
        UiLanguages languages = new UiLanguages(List.of("en", "vi"));

        assertThat(languages.narrow("vi-VN")).contains(Locale.forLanguageTag("vi"));
        assertThat(languages.narrow("en_GB")).contains(Locale.forLanguageTag("en"));
        assertThat(languages.narrow(Locale.forLanguageTag("vi-Latn-VN"))).contains(Locale.forLanguageTag("vi"));
    }

    @Test
    void answersNothingForALanguageThisDeploymentDoesNotShip() {
        UiLanguages languages = new UiLanguages(List.of("en", "vi"));

        assertThat(languages.narrow("fr")).isEmpty();
        assertThat(languages.narrow("not a tag")).isEmpty();
        assertThat(languages.narrow((String) null)).isEmpty();
        assertThat(languages.narrow((Locale) null)).isEmpty();
    }

    @Test
    void fallsBackToTheFirstConfiguredLanguage() {
        assertThat(new UiLanguages(List.of("vi", "en")).fallback()).isEqualTo(Locale.forLanguageTag("vi"));
        assertThat(new UiLanguages(List.of("en", "vi")).supported())
                .containsExactly(Locale.forLanguageTag("en"), Locale.forLanguageTag("vi"));
    }

    @Test
    void refusesAListThatNamesNoUsableLanguage() {
        assertThatThrownBy(() -> new UiLanguages(List.of(" ", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one language");
        assertThatThrownBy(() -> new UiLanguages(List.of("en", "zz")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an ISO language");
        assertThatThrownBy(() -> new UiLanguages(List.of("en", "not a tag")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a language tag");
    }
}
