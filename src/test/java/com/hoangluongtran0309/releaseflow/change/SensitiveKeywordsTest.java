package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveKeywordsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "breaking change", "migration", "security", "auth", "credential", "password", "encryption"
    })
    void everyKeywordForcesReviewAndNamesItself(String keyword) {
        assertThat(SensitiveKeywords.matches("Fix the " + keyword + " path", null))
                .extracting(ReviewTrigger::type, ReviewTrigger::detail)
                .containsExactly(tuple(keyword));
    }

    @Test
    void readsTheDescriptionAsWellAsTheTitle() {
        assertThat(SensitiveKeywords.matches("Tidy the exporter", "Rotates the stored CREDENTIAL afterwards."))
                .extracting(ReviewTrigger::detail)
                .containsExactly("credential");
    }

    @Test
    void ignoresCaseAndFindsSeveralAtOnce() {
        assertThat(SensitiveKeywords.matches("SECURITY: rotate the Password", "Needs a MIGRATION first."))
                .extracting(ReviewTrigger::detail)
                .containsExactly("migration", "security", "password");
    }

    @Test
    void saysNothingAboutAnInnocuousChange() {
        assertThat(SensitiveKeywords.matches("Add a CSV export button", "Users can download any table.")).isEmpty();
        assertThat(SensitiveKeywords.matches(null, null)).isEmpty();
    }

    private static org.assertj.core.groups.Tuple tuple(String keyword) {
        return org.assertj.core.groups.Tuple.tuple(ReviewTriggerType.SENSITIVE_KEYWORD, keyword);
    }
}
