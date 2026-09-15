package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitivePathAdditionsTest {

    @Test
    void trimsAndDropsBlankLinesAndRepeatsInOrder() {
        assertThat(SensitivePathAdditions.normalize(Arrays.asList(
                "  **/billing/** ", "", null, "   ", "infra/**", "**/billing/**"
        ))).containsExactly("**/billing/**", "infra/**");
    }

    @Test
    void acceptsAtMostOneHundredPatterns() {
        assertThat(SensitivePathAdditions.normalize(patterns(100))).hasSize(100);

        assertThatThrownBy(() -> SensitivePathAdditions.normalize(patterns(101)))
                .isInstanceOf(InvalidSensitivePathsException.class)
                .hasMessageContaining("at most 100 patterns");
    }

    @Test
    void repeatsDoNotCountTowardsTheLimit() {
        List<String> repeated = IntStream.range(0, 150).mapToObj(index -> "**/billing/**").toList();

        assertThat(SensitivePathAdditions.normalize(repeated)).containsExactly("**/billing/**");
    }

    @Test
    void acceptsPatternsOfAtMost256Characters() {
        String longest = "a".repeat(256);

        assertThat(SensitivePathAdditions.normalize(List.of(longest))).containsExactly(longest);
        assertThatThrownBy(() -> SensitivePathAdditions.normalize(List.of("a".repeat(257))))
                .isInstanceOf(InvalidSensitivePathsException.class)
                .hasMessageContaining("at most 256 characters");
    }

    @Test
    void rejectsAnInvalidGlobByName() {
        assertThatThrownBy(() -> SensitivePathAdditions.normalize(List.of("**/billing/**", "src/[unclosed")))
                .isInstanceOf(InvalidSensitivePathsException.class)
                .hasMessage("Not a valid glob pattern: src/[unclosed");
    }

    @Test
    void anEmptyListClearsTheAdditions() {
        assertThat(SensitivePathAdditions.normalize(List.of())).isEmpty();
    }

    private static List<String> patterns(int count) {
        return IntStream.range(0, count).mapToObj(index -> "module" + index + "/**").toList();
    }
}
