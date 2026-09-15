package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.AiStatus;
import com.hoangluongtran0309.releaseflow.change.ChangeCategory;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.ChangedFileStatus;
import com.hoangluongtran0309.releaseflow.change.ClassificationSource;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;
import com.hoangluongtran0309.releaseflow.change.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseNoteDigestTest {

    private static final String TEMPLATE = "- {{whatChanged}}{{#narrative}} — {{.}}{{/narrative}}";

    @Test
    void putsBreakingChangesFirstAndCountsEverySection() {
        List<ChangeView> changes = List.of(
                change(4, "docs: explain export", ChangeCategory.DOCUMENTATION, false, 4, null, Map.of()),
                change(1, "feat: add export", ChangeCategory.FEATURE, false, 1, null, Map.of()),
                change(3, "fix: handle empty tables", ChangeCategory.FIX, false, 3, null, Map.of()),
                change(2, "feat!: rename the API", ChangeCategory.FEATURE, true, 2, null, Map.of()),
                change(5, "feat: add import", ChangeCategory.FEATURE, false, 5, null, Map.of()),
                change(6, "perf: stream rows", ChangeCategory.PERFORMANCE, false, 6, null, Map.of()),
                change(7, "chore: bump plugin", ChangeCategory.MAINTENANCE, false, 7, null, Map.of())
        );

        assertThat(ReleaseNoteDigest.render("2.0.0", "  A big one.  ", changes, TEMPLATE, "operator", "en")).isEqualTo("""
                # Release 2.0.0

                A big one.

                ## What's New

                This release includes 7 changes: 1 breaking change, 2 new features, 1 bug fix, 1 performance improvement, 1 documentation update, and 1 maintenance change.

                > ⚠️ 1 breaking change requires action before upgrading.

                ## ⚠️ Breaking Changes

                - rename the API

                ## ✨ New Features

                - add export

                - add import

                ## 🐛 Bug Fixes

                - handle empty tables

                ## ⚡ Performance

                - stream rows

                ## 📚 Documentation

                - explain export

                ## 🔧 Maintenance

                - bump plugin
                """);
    }

    @Test
    void leavesOutEmptySectionsAndTheBreakingWarning() {
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", ChangeCategory.FEATURE, false, 1, null, Map.of()),
                change(2, "fix: trim input", ChangeCategory.FIX, false, 2, null, Map.of())
        );

        String note = ReleaseNoteDigest.render("1.1.0", null, changes, TEMPLATE, "operator", "en");

        assertThat(note).startsWith("# Release 1.1.0\n\n## What's New\n\n"
                + "This release includes 2 changes: 1 new feature and 1 bug fix.\n\n## ✨ New Features");
        assertThat(note).doesNotContain("Breaking", "> ", "Documentation");
    }

    @Test
    void writesVietnameseLabelsForVietnamese() {
        List<ChangeView> changes = List.of(
                change(1, "feat: add export", ChangeCategory.FEATURE, false, 1, null, Map.of()),
                change(2, "feat!: drop v1", ChangeCategory.FEATURE, true, 2, null, Map.of()),
                change(3, "fix: trim", ChangeCategory.FIX, false, 3, null, Map.of()),
                change(4, "fix: pad", ChangeCategory.FIX, false, 4, null, Map.of())
        );

        String note = ReleaseNoteDigest.render("1.2.0", null, changes, TEMPLATE, "operator", "vi-VN");

        assertThat(note)
                .startsWith("# Bản phát hành 1.2.0\n\n## Có gì mới\n\n"
                        + "Bản phát hành này gồm 4 thay đổi: 1 thay đổi phá vỡ, 1 tính năng mới và 2 lỗi được sửa.\n\n"
                        + "> ⚠️ 1 thay đổi phá vỡ cần được xử lý trước khi nâng cấp.")
                .contains("## ⚠️ Thay đổi phá vỡ", "## ✨ Tính năng mới", "## 🐛 Sửa lỗi");
    }

    @Test
    void fallsBackToEnglishForOtherLanguagesAndUsesPlurals() {
        List<ChangeView> changes = List.of(
                change(1, "feat!: a", ChangeCategory.FEATURE, true, 1, null, Map.of()),
                change(2, "feat!: b", ChangeCategory.FIX, true, 2, null, Map.of())
        );

        assertThat(ReleaseNoteDigest.render("3.0.0", null, changes, TEMPLATE, "operator", "ja"))
                .contains("This release includes 2 changes: 2 breaking changes.")
                .contains("> ⚠️ 2 breaking changes require action before upgrading.");
    }

    @Test
    void usesTheSummaryAndTheAudiencesOwnNarrative() {
        NeutralSummary summary = new NeutralSummary("Tables export as CSV.", "Users asked.", "Streams rows.", "Nothing.");
        ChangeView change = change(1, "feat: add export", ChangeCategory.FEATURE, false, 1, summary,
                Map.of("operator", "Watch the export queue.", "end_user", "Download any table."));
        String template = "- {{whatChanged}} ({{whyChanged}}; {{technicalDetail}}; {{migrationStep}}){{#narrative}} — {{.}}{{/narrative}}";

        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "end_user", "en"))
                .contains("- Tables export as CSV. (Users asked.; Streams rows.; Nothing.) — Download any table.");
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), template, "contributor", "en"))
                .contains("- Tables export as CSV. (Users asked.; Streams rows.; Nothing.)\n");
    }

    @Test
    void escapesAPullRequestTitleUsedInsteadOfASummary() {
        ChangeView change = change(1, "feat: add *bold* [link](x) <b>", ChangeCategory.FEATURE, false, 1, null, Map.of());

        assertThat(ReleaseNoteDigest.render("1.0.0_rc", null, List.of(change), TEMPLATE, "operator", "en"))
                .startsWith("# Release 1.0.0\\_rc")
                .contains("- add \\*bold\\* \\[link\\](x) \\<b\\>");
    }

    @Test
    void demotesHeadingsInsideItemsButNotInCode() {
        assertThat(ReleaseNoteDigest.demoteHeadings("## What changed\ntext\n### Detail\n##### five\n#tag\n```\n# code\n```\n# top"))
                .isEqualTo("#### What changed\ntext\n##### Detail\n###### five\n#tag\n```\n# code\n```\n### top");

        ChangeView change = change(1, "feat: export", ChangeCategory.FEATURE, false, 1,
                new NeutralSummary("## Export", "", "", ""), Map.of());
        assertThat(ReleaseNoteDigest.render("1.0.0", null, List.of(change), "{{whatChanged}}", "operator", "en"))
                .contains("## ✨ New Features\n\n#### Export\n");
    }

    @Test
    void refusesAReleaseWithoutChanges() {
        assertThatThrownBy(() -> ReleaseNoteDigest.render("1.0.0", null, List.of(), TEMPLATE, "operator", "en"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChangeView change(
            int number,
            String title,
            ChangeCategory category,
            boolean breaking,
            int minute,
            NeutralSummary summary,
            Map<String, String> narratives
    ) {
        return new ChangeView(
                UUID.randomUUID(),
                number,
                title,
                null,
                "mai-dev",
                List.of(),
                "main",
                "a".repeat(40),
                Instant.parse("2026-09-01T10:00:00Z").plusSeconds(minute * 60L),
                "https://github.com/acme/releaseflow/pull/" + number,
                category,
                breaking,
                false,
                List.of(),
                ClassificationSource.RULES,
                summary == null ? AiStatus.NOT_REQUESTED : AiStatus.SUCCEEDED,
                null,
                null,
                false,
                null,
                null,
                null,
                ProcessingStatus.COMPLETED,
                ChangedFileStatus.COLLECTED,
                List.of(),
                List.of(),
                summary,
                summary == null ? null : "en",
                null,
                narratives,
                null,
                null
        );
    }
}
