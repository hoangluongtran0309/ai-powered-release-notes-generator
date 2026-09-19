package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackWebhookUrlTest {

    private final SlackWebhookUrl webhookUrl = new SlackWebhookUrl("hooks.slack.com,hooks.slack-gov.com");

    @Test
    void acceptsOnlyAnIncomingWebhookOnAnAllowedHost() {
        assertThatCode(() -> webhookUrl.validated("https://hooks.slack.com/services/T0/B0/secret"))
                .doesNotThrowAnyException();
        assertThat(webhookUrl.validated("https://hooks.slack-gov.com/services/T0/B0/secret").getHost())
                .isEqualTo("hooks.slack-gov.com");
    }

    @Test
    void refusesAnAddressThatIsNotSlacksOwn() {
        assertThatThrownBy(() -> webhookUrl.validated("https://example.com/services/T0/B0/secret"))
                .isInstanceOf(AutomationActionInvalidException.class);
        // Plain HTTP is not allowed unless a deployment writes the scheme into its allowlist.
        assertThatThrownBy(() -> webhookUrl.validated("http://hooks.slack.com/services/T0/B0/secret"))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> webhookUrl.validated("https://hooks.slack.com:8443/services/T0/B0/secret"))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> webhookUrl.validated("https://user:pass@hooks.slack.com/services/T0/B0/secret"))
                .isInstanceOf(AutomationActionInvalidException.class);
    }

    @Test
    void refusesAnythingButTheIncomingWebhookPath() {
        assertThatThrownBy(() -> webhookUrl.validated("https://hooks.slack.com/api/chat.postMessage"))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> webhookUrl.validated("https://hooks.slack.com/services/"))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> webhookUrl.validated("https://hooks.slack.com/services/T0?x=1"))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> webhookUrl.validated("https://hooks.slack.com/services/T0#f"))
                .isInstanceOf(AutomationActionInvalidException.class);
    }

    @Test
    void saysWhatIsMissingRatherThanWhatIsWrong() {
        assertThatThrownBy(() -> webhookUrl.validated(null))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_slack_webhook_required");
        assertThatThrownBy(() -> webhookUrl.validated("  "))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_slack_webhook_required");
    }

    @Test
    void refusesAnAllowlistNamingNothing() {
        assertThatThrownBy(() -> new SlackWebhookUrl(" , "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SlackWebhookUrl("not a host"))
                .isInstanceOf(IllegalStateException.class);
    }
}
