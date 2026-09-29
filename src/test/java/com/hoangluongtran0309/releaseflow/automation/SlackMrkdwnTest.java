package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackMrkdwnTest {

    // The shape a shipped audience template gives a note.
    private static final String NOTE = """
            # Release 1.4.0

            Apple Pay, saved addresses, and the end of the legacy /v1/cart API.

            ## What's New

            > ⚠️ 1 breaking change requires action before upgrading.

            ## ⚠️ Breaking Changes

            - **The legacy /v1/cart API endpoints have been removed.** ([#104](https://github.com/acme/checkout-web/pull/104)) — Move to /v2/cart.
              - Why: /v2/cart has been available since March.
              - Migration: Update all API calls.
            - **Apple Pay was added.** ([#101](https://github.com/acme/checkout-web/pull/101))
            """;

    @Test
    void writesHeadingsAndStrongTextAsSlackBold() {
        String message = SlackMrkdwn.render(NOTE);

        assertThat(message).startsWith("*Release 1.4.0*\n\nApple Pay, saved addresses");
        assertThat(message).contains("\n\n*What's New*\n\n", "\n\n*⚠️ Breaking Changes*\n\n");
        assertThat(message).doesNotContain("# ", "**");
    }

    @Test
    void writesLinksBulletsAndQuotesTheWaySlackReadsThem() {
        String message = SlackMrkdwn.render(NOTE);

        assertThat(message).contains("> ⚠️ 1 breaking change requires action before upgrading.");
        assertThat(message).contains(
                "• *The legacy /v1/cart API endpoints have been removed.* "
                        + "(<https://github.com/acme/checkout-web/pull/104|#104>) — Move to /v2/cart.\n"
                        + "    • Why: /v2/cart has been available since March.\n"
                        + "    • Migration: Update all API calls.\n"
                        + "• *Apple Pay was added.* (<https://github.com/acme/checkout-web/pull/101|#101>)");
        assertThat(message).doesNotContain("](");
    }

    @Test
    void neverLetsTheNoteMentionAnyone() {
        String message = SlackMrkdwn.render("Ping <!channel> and <@U123> & <!subteam^S1> or <#C1|general>");

        assertThat(message).isEqualTo(
                "Ping &lt;!channel&gt; and &lt;@U123&gt; &amp; &lt;!subteam^S1&gt; or &lt;#C1|general&gt;");
    }

    @Test
    void writesAnAutolinkAsTheAddressAlone() {
        assertThat(SlackMrkdwn.render("See <https://example.com/changelog>."))
                .isEqualTo("See <https://example.com/changelog>.");
    }

    @Test
    void keepsOnlyTheTextOfALinkToAnUnsafeTarget() {
        assertThat(SlackMrkdwn.render("[click](javascript:alert(1)) and [mail](mailto:team@example.com)"))
                .isEqualTo("click and <mailto:team@example.com|mail>");
        assertThat(SlackMrkdwn.render("[x](https://example.com/a|b>c)"))
                .isEqualTo("<https://example.com/a%7Cb%3Ec|x>");
    }

    @Test
    void keepsCodeAndEmphasis() {
        assertThat(SlackMrkdwn.render("Use `a<b` and _care_.\n\n```\nx > 1\n```"))
                .isEqualTo("Use `a&lt;b` and _care_.\n\n```\nx &gt; 1\n```");
    }

    @Test
    void numbersAnOrderedListFromItsOwnStart() {
        assertThat(SlackMrkdwn.render("3. three\n4. four")).isEqualTo("3. three\n4. four");
    }

    @Test
    void titlesANoteOnlyWhenItHasNoHeadingOfItsOwn() {
        assertThat(SlackActionExecutor.slackMessage("1.4.0", "# Release 1.4.0\n\nBody."))
                .isEqualTo("*Release 1.4.0*\n\nBody.");
        assertThat(SlackActionExecutor.slackMessage("1.4.0", "- **A** change"))
                .isEqualTo("*Release 1.4.0*\n\n• *A* change");
        assertThat(SlackActionExecutor.slackMessage("<2.0>", "")).isEqualTo("*Release &lt;2.0&gt;*");
    }
}
