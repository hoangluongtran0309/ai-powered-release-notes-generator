package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JiraKeysTest {

    @Test
    void findsTheProjectsKeysInFirstSeenOrderOnce() {
        List<String> evidence = List.of(
                "feat: export (APP-12)",
                "Closes app-7 and APP-12.",
                "feature/APP-3",
                "Refs OPS-9 and APP-7"
        );

        assertThat(JiraKeys.extract("APP", evidence)).containsExactly("APP-12", "APP-7", "APP-3");
    }

    // The old project's guard: a key followed by a hyphen, as in APP-3-export, is not taken.
    @ParameterizedTest
    @ValueSource(strings = {"feature/APP-3-export", "XAPP-9", "APP-99x", "APP-0", "APP-", "APP_1", "APP-1-2", "MYAPP-4"})
    void ignoresSomethingThatOnlyLooksLikeAKey(String text) {
        assertThat(JiraKeys.extract("APP", List.of(text))).isEmpty();
    }

    @Test
    void stopsAtTenKeysAcrossAllEvidence() {
        List<String> evidence = new ArrayList<>();
        evidence.add(String.join(" ", IntStream.rangeClosed(1, 6).mapToObj(n -> "APP-" + n).toList()));
        evidence.add(String.join(" ", IntStream.rangeClosed(7, 15).mapToObj(n -> "APP-" + n).toList()));

        assertThat(JiraKeys.extract("APP", evidence))
                .hasSize(JiraKeys.MAX_KEYS)
                .startsWith("APP-1")
                .endsWith("APP-10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "1APP", "AP-P", "A.B"})
    void aMalformedProjectKeyFindsNothing(String projectKey) {
        assertThat(JiraKeys.extract(projectKey, List.of("1APP-1 AP-P-1 A.B-1 -1"))).isEmpty();
    }

    @Test
    void quotesTheProjectKeyAndSkipsMissingEvidence() {
        List<String> evidence = new ArrayList<>();
        evidence.add(null);
        evidence.add("A_B-5 AXB-6");

        assertThat(JiraKeys.extract("a_b", evidence)).containsExactly("A_B-5");
        assertThat(JiraKeys.extract(null, evidence)).isEmpty();
    }
}
