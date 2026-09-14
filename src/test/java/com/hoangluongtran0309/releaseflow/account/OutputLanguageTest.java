package com.hoangluongtran0309.releaseflow.account;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputLanguageTest {

    @ParameterizedTest
    @CsvSource({
            "en, en, English",
            "VI, vi, Vietnamese",
            "vi_VN, vi-VN, Vietnamese (Vietnam)",
            "' pt-br ', pt-BR, Portuguese (Brazil)",
            "zh-Hant-TW, zh-Hant-TW, 'Chinese (Traditional, Taiwan)'"
    })
    void canonicalizesLanguageTags(String input, String tag, String displayName) {
        OutputLanguage language = OutputLanguage.parse(input);

        assertThat(language.tag()).isEqualTo(tag);
        assertThat(language.displayName()).isEqualTo(displayName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "xx", "english", "e", "en--US", "12"})
    void rejectsInvalidTags(String input) {
        assertThatThrownBy(() -> OutputLanguage.parse(input)).isInstanceOf(InvalidOutputLanguageException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh-Hant-TW-u-ca-chinese"})
    void rejectsTagsLongerThanSixteenCharacters(String input) {
        assertThatThrownBy(() -> OutputLanguage.parse(input))
                .isInstanceOf(InvalidOutputLanguageException.class)
                .hasMessageContaining("16");
    }
}
