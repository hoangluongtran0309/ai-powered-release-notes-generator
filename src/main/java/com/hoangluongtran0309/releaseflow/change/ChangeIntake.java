package com.hoangluongtran0309.releaseflow.change;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a merged pull request as a change with its processing job, whether a webhook
 * delivered it or an import found it. A source's pull request is recorded once, so
 * the two never duplicate each other.
 */
@Component
class ChangeIntake {

    static final String SOURCE_EXTERNAL_UNIQUE_CONSTRAINT = "changes_source_external_unique";

    private final ChangeRepository changeRepository;
    private final ChangeProcessingJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    ChangeIntake(
            ChangeRepository changeRepository,
            ChangeProcessingJobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.jobRepository = jobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @param deliveryId the webhook delivery, or {@code null} for an import
     */
    Outcome record(
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            MergedPullRequest pullRequest,
            ChangeOrigin origin,
            UUID deliveryId
    ) {
        if (changeRepository.existsBySourceIdAndExternalId(sourceId, Change.externalId(pullRequest))) {
            return Outcome.DUPLICATE;
        }
        // The change and its processing job are recorded together; changed files are
        // collected and the change classified later, outside this call.
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Instant receivedAt = clock.instant();
                Change change = changeRepository.saveAndFlush(Change.received(
                        UUID.randomUUID(),
                        organizationId,
                        projectId,
                        sourceId,
                        pullRequest,
                        origin,
                        deliveryId,
                        receivedAt
                ));
                jobRepository.save(new ChangeProcessingJob(UUID.randomUUID(), change, receivedAt));
            });
        } catch (DataIntegrityViolationException exception) {
            // A concurrent delivery or import of the same pull request committed first.
            if (violates(exception, SOURCE_EXTERNAL_UNIQUE_CONSTRAINT)) {
                return Outcome.DUPLICATE;
            }
            throw exception;
        }
        return Outcome.RECORDED;
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    enum Outcome {
        RECORDED,
        DUPLICATE
    }
}
