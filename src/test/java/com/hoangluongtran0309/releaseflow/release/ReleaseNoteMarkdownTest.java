package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNoteMarkdownTest {

    @Test
    void rendersHeadingSummaryAndSectionsWithLinks() {
        String markdown = ReleaseNoteMarkdown.render("1.4.0", "  Exports and clearer config.  ", List.of(
                new ReleaseNoteSection("Breaking changes", List.of(item(2, "rename config keys", ChangeCategory.FIX, true))),
                new ReleaseNoteSection("Features", List.of(
                        item(1, "add CSV export", ChangeCategory.FEATURE, false),
                        item(3, "add the inbox", ChangeCategory.FEATURE, false)
                ))
        ));

        assertThat(markdown).isEqualTo("""
                # 1.4.0

                Exports and clearer config.

                ## Breaking changes

                - rename config keys ([#2](https://github.com/acme/releaseflow/pull/2)) — Fix

                ## Features

                - add CSV export ([#1](https://github.com/acme/releaseflow/pull/1))
                - add the inbox ([#3](https://github.com/acme/releaseflow/pull/3))
                """);
    }

    @Test
    void omitsAMissingSummaryAndEscapesMarkdownInText() {
        String markdown = ReleaseNoteMarkdown.render("2.0.0_rc", null, List.of(
                new ReleaseNoteSection("Fixes", List.of(item(7, "handle *bold* [links] <tags> `code` \\ and _under_", ChangeCategory.FIX, false)))
        ));

        assertThat(markdown).isEqualTo("""
                # 2.0.0\\_rc

                ## Fixes

                - handle \\*bold\\* \\[links\\] \\<tags\\> \\`code\\` \\\\ and \\_under\\_ ([#7](https://github.com/acme/releaseflow/pull/7))
                """);
    }

    private static ReleaseNoteItem item(int number, String title, ChangeCategory category, boolean breaking) {
        return new ReleaseNoteItem(
                UUID.randomUUID(),
                number,
                title,
                "https://github.com/acme/releaseflow/pull/" + number,
                category,
                breaking
        );
    }
}
