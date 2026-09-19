package com.hoangluongtran0309.releaseflow.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Sends the release note to a fixed list of addresses over the deployment's own SMTP
 * server. An Action has no mail credentials of its own, so a Rule that needs one can
 * only be enabled once the deployment is configured to send at all.
 */
@Component
class EmailActionExecutor implements RuleActionExecutor {

    static final String NOT_CONFIGURED = "email_not_configured";
    static final String RECIPIENTS_INVALID = "email_recipients_invalid";
    static final String REJECTED = "email_rejected";

    private static final Logger log = LoggerFactory.getLogger(EmailActionExecutor.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;

    EmailActionExecutor(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String host,
            @Value("${releaseflow.automation.email.from:}") String from
    ) {
        this.mailSender = mailSender;
        this.host = host == null ? "" : host.strip();
        this.from = from == null ? "" : from.strip();
    }

    @Override
    public ActionType actionType() {
        return ActionType.EMAIL;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        if (rawSecret != null && !rawSecret.isBlank()) {
            throw AutomationActionInvalidException.secretNotAllowed(ActionType.EMAIL);
        }
        EmailRecipients.parse(configuration.get(EmailRecipients.KEY));
    }

    @Override
    public void validateAvailability() {
        if (!configured()) {
            throw AutomationConflictException.emailNotConfigured();
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        requireNoTransaction();
        if (!configured()) {
            return ActionResult.failed(NOT_CONFIGURED);
        }
        final List<String> recipients;
        try {
            recipients = EmailRecipients.parse(command.configuration().get(EmailRecipients.KEY));
        } catch (AutomationActionInvalidException exception) {
            return ActionResult.failed(RECIPIENTS_INVALID);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject("Release " + command.releaseVersion());
        message.setText(command.noteContent());
        try {
            mailSender.getObject().send(message);
            return ActionResult.succeeded(null);
        } catch (MailSendException exception) {
            // SMTP may have accepted the message for some or all of the recipients.
            log.warn("SMTP did not confirm the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        } catch (MailException exception) {
            log.warn("SMTP refused the note of release {}.", command.releaseId());
            return ActionResult.failed(REJECTED);
        }
    }

    private boolean configured() {
        return !host.isBlank() && !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SMTP must not be called inside a database transaction.");
        }
    }
}
