package com.hoangluongtran0309.releaseflow.category;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRefTest {

    @ParameterizedTest
    @CsvSource({
            "feature, FEATURE",
            "' Bug fix ', BUG_FIX",
            "audit-log, AUDIT_LOG",
            "A1_B2, A1_B2"
    })
    void normalizesCodes(String value, String code) {
        assertThat(CategoryRef.normalize(value)).contains(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "9lives", "_hidden", "dot.ted", "ümlaut"})
    void rejectsInvalidCodes(String value) {
        assertThat(CategoryRef.normalize(value)).isEmpty();
    }

    @Test
    void limitsCodesTo64Characters() {
        assertThat(CategoryRef.normalize("A".repeat(64))).isPresent();
        assertThat(CategoryRef.normalize("A".repeat(65))).isEmpty();
        assertThat(CategoryRef.normalize(null)).isEmpty();
    }
}
