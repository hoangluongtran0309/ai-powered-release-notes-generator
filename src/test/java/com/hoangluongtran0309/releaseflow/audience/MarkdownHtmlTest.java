package com.hoangluongtran0309.releaseflow.audience;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownHtmlTest {

    @Test
    void rendersMarkdown() {
        assertThat(MarkdownHtml.render("## New\n\n- **Export** — `csv`\n"))
                .isEqualTo("<h2>New</h2>\n<ul>\n<li><strong>Export</strong> — <code>csv</code></li>\n</ul>\n");
    }

    @Test
    void escapesRawHtml() {
        assertThat(MarkdownHtml.render("<script>alert(1)</script>\n\nHi <img src=x onerror=alert(1)>"))
                .doesNotContain("<script>")
                .doesNotContain("<img")
                .contains("&lt;script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void neutralizesUnsafeLinksAndMarksLinksAsExternal() {
        assertThat(MarkdownHtml.render("[click](javascript:alert(1)) and [docs](https://example.com)"))
                .doesNotContain("javascript:")
                .contains("<a rel=\"nofollow noopener noreferrer\" href=\"https://example.com\">docs</a>");
    }

    @Test
    void turnsImagesIntoLinks() {
        String html = MarkdownHtml.render("![tracking pixel](https://tracker.example/p.gif) ![](https://x.example/a.png)");

        assertThat(html)
                .doesNotContain("<img")
                .contains("<a href=\"https://tracker.example/p.gif\" rel=\"nofollow noopener noreferrer\">tracking pixel</a>")
                .contains(">https://x.example/a.png</a>");
    }

    @Test
    void rendersNothingForEmptyText() {
        assertThat(MarkdownHtml.render(null)).isEmpty();
        assertThat(MarkdownHtml.render("  ")).isEmpty();
    }
}
