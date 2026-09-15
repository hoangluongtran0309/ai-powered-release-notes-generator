package com.hoangluongtran0309.releaseflow.release;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNoteMarkdownTest {

    @Test
    void escapesMarkdownSyntaxInUntrustedText() {
        assertThat(ReleaseNoteMarkdown.escape("handle *bold* [links] <tags> `code` \\ and _under_"))
                .isEqualTo("handle \\*bold\\* \\[links\\] \\<tags\\> \\`code\\` \\\\ and \\_under\\_");
    }

    @Test
    void leavesPlainTextAlone() {
        assertThat(ReleaseNoteMarkdown.escape("Add CSV export (#12).")).isEqualTo("Add CSV export (#12).");
    }
}
