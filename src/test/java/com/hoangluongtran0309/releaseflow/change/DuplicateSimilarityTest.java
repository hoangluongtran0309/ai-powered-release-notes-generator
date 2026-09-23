package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DuplicateSimilarityTest {

    @Test
    void comparesCharacterTrigramsAfterNormalizing() {
        assertThat(DuplicateSimilarity.jaccard(DuplicateSimilarity.trigrams("abcd"), DuplicateSimilarity.trigrams("abce")))
                .isEqualTo(1.0 / 3);
        assertThat(DuplicateSimilarity.trigrams("Ｆｉｘ—LOGIN!!")).containsExactlyInAnyOrder(
                "fix", "ix ", "x l", " lo", "log", "ogi", "gin");
        assertThat(DuplicateSimilarity.trigrams("ab")).containsExactly("ab");
        assertThat(DuplicateSimilarity.trigrams("  ")).isEmpty();
    }

    @Test
    void identicalTextWithoutFilesIsAPerfectMatch() {
        DuplicateSimilarity.Match match = DuplicateSimilarity.compare(
                subject("Fix account recovery email", "Send the recovery email once", Set.of()),
                subject("Fix account recovery email", "Send the recovery email once", Set.of("src/Recovery.java"))
        );

        assertThat(match.score()).isEqualTo(1.0);
        assertThat(match.paths()).isZero();
    }

    @Test
    void filesCountOnlyWhenBothSidesListThem() {
        DuplicateSimilarity.Match disjoint = DuplicateSimilarity.compare(
                subject("Bump dependencies", "Weekly update", Set.of("pom.xml")),
                subject("Bump dependencies", "Weekly update", Set.of("package.json"))
        );
        DuplicateSimilarity.Match overlapping = DuplicateSimilarity.compare(
                subject("Bump dependencies", "Weekly update", Set.of("pom.xml", "a", "b")),
                subject("Bump dependencies", "Weekly update", Set.of("pom.xml", "c"))
        );

        assertThat(disjoint.score()).isCloseTo(0.80, within(1e-9));
        assertThat(overlapping.paths()).isEqualTo(0.25);
        assertThat(overlapping.score()).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void theTitleAloneIsNotEnough() {
        DuplicateSimilarity.Match match = DuplicateSimilarity.compare(
                subject("Update README", "Explain the webhook setup", Set.of()),
                subject("Update README", "Remove the old screenshots", Set.of())
        );

        assertThat(match.title()).isEqualTo(1.0);
        assertThat(match.score()).isLessThan(0.82);
    }

    private static DuplicateSimilarity.Subject subject(String title, String content, Set<String> paths) {
        return new DuplicateSimilarity.Subject(title, content, paths);
    }
}
