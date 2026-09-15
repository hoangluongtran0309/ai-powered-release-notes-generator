package com.hoangluongtran0309.releaseflow.audience;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped audiences must read differently for the same change, not only in a few
 * words. After removing the lines two notes share, what is left of them must be less
 * than 90% alike (normalized Levenshtein similarity), as in the earlier product.
 */
class AudiencePresetDivergenceTest {

    private static final double DUPLICATE_THRESHOLD = 0.90;
    private static final Map<String, String> NARRATIVES = Map.of(
            AudiencePresets.OPERATOR, "Exports now stream from a background queue; watch queue depth and roll back by disabling the export flag.",
            AudiencePresets.CONTRIBUTOR, "The exporter moved to ExportJob, so callers of TableExporter.export must switch to the new async API.",
            AudiencePresets.END_USER, "You can download any table as a spreadsheet, even very large ones."
    );

    @Test
    void theShippedAudiencesWriteDifferentNotesForTheSameChange() {
        for (String language : List.of("en", "vi")) {
            List<String> notes = AudiencePresets.forLanguage(language).stream()
                    .map(preset -> AudienceTemplate.render(preset.templateBody(), item(NARRATIVES.get(preset.code()))))
                    .toList();
            for (int left = 0; left < notes.size(); left++) {
                for (int right = left + 1; right < notes.size(); right++) {
                    assertThat(residualSimilarity(notes.get(left), notes.get(right)))
                            .as("%s notes %d and %d", language, left, right)
                            .isLessThan(DUPLICATE_THRESHOLD);
                }
            }
        }
    }

    @Test
    void identicalNotesCountAsDuplicates() {
        String note = AudienceTemplate.render(AudiencePresets.forLanguage("en").getFirst().templateBody(), item("Same."));

        assertThat(similarity(note, note)).isEqualTo(1.0);
    }

    private static AudienceItem item(String narrative) {
        return new AudienceItem(
                "Large tables export without timeouts.",
                "Exports of big tables failed after 30 seconds.",
                "Rows are streamed in pages of 1000 through a queue.",
                "Set EXPORT_QUEUE_ENABLED=true before upgrading.",
                narrative,
                42,
                "https://github.com/acme/app/pull/42"
        );
    }

    static double residualSimilarity(String left, String right) {
        return similarity(stripSharedLines(left, right), stripSharedLines(right, left));
    }

    private static String stripSharedLines(String content, String other) {
        Set<String> shared = new HashSet<>(other.lines().map(AudiencePresetDivergenceTest::normalize).toList());
        return content.lines()
                .map(AudiencePresetDivergenceTest::normalize)
                .filter(line -> !line.isEmpty() && !shared.contains(line))
                .collect(Collectors.joining("\n"));
    }

    static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
