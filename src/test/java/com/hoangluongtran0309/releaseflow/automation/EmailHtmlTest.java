package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlTest {

    @Test
    void rendersTheNoteAsAWholeDocumentInItsLanguage() {
        String html = EmailActionExecutor.emailHtml("vi", """
                ## Release 1.4.0

                - **Apple Pay was added.** ([#101](https://github.com/acme/checkout-web/pull/101))
                """);

        assertThat(html)
                .startsWith("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\"></head><body>")
                .endsWith("</body></html>")
                .contains("<h2>Release 1.4.0</h2>")
                .contains("<strong>Apple Pay was added.</strong>")
                .contains("href=\"https://github.com/acme/checkout-web/pull/101\"")
                .doesNotContain("**", "##");
    }

    @Test
    void escapesHtmlTheNoteCarries() {
        String html = EmailActionExecutor.emailHtml("en\"><script>", "Fixed <script>alert(1)</script> in titles.");

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .startsWith("<!doctype html><html lang=\"en&quot;&gt;&lt;script&gt;\">");
    }

    @Test
    void turnsAnImageIntoALinkSoOpeningTheMailLoadsNothing() {
        String html = EmailActionExecutor.emailHtml("en", "![chart](https://example.com/chart.png)");

        assertThat(html)
                .doesNotContain("<img")
                .contains("<a href=\"https://example.com/chart.png\"");
    }
}
