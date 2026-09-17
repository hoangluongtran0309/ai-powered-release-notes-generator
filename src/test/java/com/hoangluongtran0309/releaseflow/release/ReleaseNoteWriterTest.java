package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.AiStatus;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.ChangedFileStatus;
import com.hoangluongtran0309.releaseflow.change.ClassificationSource;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.change.ProcessingStatus;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import com.hoangluongtran0309.releaseflow.translation.TranslationState.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNoteWriterTest {

    @Test
    void aNoteIsOnlyAsReadyAsItsLeastReadyChange() {
        assertThat(ReleaseNoteWriter.worse(Status.READY, Status.READY)).isEqualTo(Status.READY);
        assertThat(ReleaseNoteWriter.worse(Status.READY, Status.PENDING)).isEqualTo(Status.PENDING);
        assertThat(ReleaseNoteWriter.worse(Status.PENDING, Status.FAILED)).isEqualTo(Status.FAILED);
        assertThat(ReleaseNoteWriter.worse(Status.FAILED, Status.READY)).isEqualTo(Status.FAILED);
    }

    @Test
    void translatesTheSummaryAndEveryNarrative() {
        ChangeView change = change(new NeutralSummary("Tables export as CSV.", "Users asked.", "", ""),
                Map.of("operator", "Watch the queue.", "end_user", ""));

        Map<String, String> texts = ReleaseNoteWriter.texts(change);
        assertThat(texts).containsExactly(
                Map.entry("whatChanged", "Tables export as CSV."),
                Map.entry("whyChanged", "Users asked."),
                Map.entry("technicalDetail", ""),
                Map.entry("migrationStep", ""),
                Map.entry("narrative.end_user", ""),
                Map.entry("narrative.operator", "Watch the queue.")
        );

        Map<String, String> translated = new LinkedHashMap<>(texts);
        translated.replaceAll((key, text) -> text.isEmpty() ? text : "[VI] " + text);
        ChangeView vietnamese = ReleaseNoteWriter.translated(change, translated, "vi");
        assertThat(vietnamese.neutralSummary())
                .isEqualTo(new NeutralSummary("[VI] Tables export as CSV.", "[VI] Users asked.", "", ""));
        assertThat(vietnamese.narrative("operator")).isEqualTo("[VI] Watch the queue.");
        assertThat(vietnamese.narrative("end_user")).isEmpty();
        assertThat(vietnamese.contentLanguage()).isEqualTo("vi");
        assertThat(vietnamese.id()).isEqualTo(change.id());
        assertThat(vietnamese.title()).isEqualTo(change.title());
    }

    private static ChangeView change(NeutralSummary summary, Map<String, String> narratives) {
        return new ChangeView(
                UUID.randomUUID(), 1, "feat: add export", null, "mai-dev", List.of(), "main", "a".repeat(40),
                Instant.parse("2026-09-01T10:00:00Z"), "https://github.com/acme/releaseflow/pull/1",
                TestCategories.FEATURE.code(), TestCategories.FEATURE.displayName(), TestCategories.FEATURE.group(),
                false, false, List.of(), ClassificationSource.RULES, AiStatus.SUCCEEDED, null, null, false, null, null,
                null, ProcessingStatus.COMPLETED, ChangedFileStatus.COLLECTED, null, List.of(), List.of(), List.of(), summary, "en", null,
                narratives, null, null, null, null, null
        );
    }
}
