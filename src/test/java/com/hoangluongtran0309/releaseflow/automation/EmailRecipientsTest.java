package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailRecipientsTest {

    @Test
    void readsTheAddressesOnceEachInTheOrderGiven() {
        assertThat(EmailRecipients.parse(" ops@example.com , support@example.com, ops@example.com "))
                .containsExactly("ops@example.com", "support@example.com");
    }

    @Test
    void refusesAListNobodyCouldSendTo() {
        assertThatThrownBy(() -> EmailRecipients.parse(null))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> EmailRecipients.parse(" , "))
                .isInstanceOf(AutomationActionInvalidException.class);
        assertThatThrownBy(() -> EmailRecipients.parse("not-an-address"))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_email_recipient_invalid");
    }

    @Test
    void refusesMoreAddressesThanAnActionMayCarry() {
        String tooMany = IntStream.rangeClosed(0, EmailRecipients.MAX_RECIPIENTS)
                .mapToObj(index -> "person" + index + "@example.com")
                .collect(Collectors.joining(","));
        assertThatThrownBy(() -> EmailRecipients.parse(tooMany))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo("automation_email_recipients_required");
    }
}
