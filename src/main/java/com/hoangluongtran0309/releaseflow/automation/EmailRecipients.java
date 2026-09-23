package com.hoangluongtran0309.releaseflow.automation;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import java.util.Arrays;
import java.util.List;

/** The addresses an email Action sends to, read from its configuration. */
final class EmailRecipients {

    static final String KEY = "recipients";
    static final int MAX_RECIPIENTS = 100;

    private EmailRecipients() {
    }

    static List<String> parse(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.emailRecipientsRequired();
        }
        List<String> recipients = Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(recipient -> !recipient.isBlank())
                .distinct()
                .toList();
        if (recipients.isEmpty() || recipients.size() > MAX_RECIPIENTS) {
            throw AutomationActionInvalidException.emailRecipientsRequired();
        }
        recipients.forEach(EmailRecipients::requireAddress);
        return recipients;
    }

    private static void requireAddress(String recipient) {
        try {
            new InternetAddress(recipient, true).validate();
        } catch (AddressException exception) {
            throw AutomationActionInvalidException.emailRecipientInvalid(recipient);
        }
    }
}
