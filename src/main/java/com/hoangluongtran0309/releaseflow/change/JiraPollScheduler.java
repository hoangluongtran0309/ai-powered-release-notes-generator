package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.PolledSource;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Turns a polled source's schedule into work. Nobody asks for a poll, so this is the one
 * place in ReleaseFlow that creates a job row on its own: every other job exists because a
 * delivery arrived, an administrator clicked, or a release was approved.
 *
 * <p>The window reaches back before the cursor, because a tracker can record an issue as
 * done slightly after it changed, and reading the same issue twice is free — intake keeps
 * only the first.
 */
@Component
class JiraPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(JiraPollScheduler.class);

    private final SourceAccess sourceAccess;
    private final SourceSyncJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration overlap;

    JiraPollScheduler(
            SourceAccess sourceAccess,
            SourceSyncJobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Value("${releaseflow.jira.poll-overlap}") Duration overlap
    ) {
        this.sourceAccess = sourceAccess;
        this.jobRepository = jobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.overlap = overlap;
    }

    void scheduleDuePolls(Instant now) {
        List<PolledSource> due = sourceAccess.findDuePolls(SourceType.JIRA, now);
        for (PolledSource source : due) {
            if (jobRepository.hasActiveJob(source.id())) {
                // The previous poll has not finished; it books the next one when it does.
                continue;
            }
            Instant windowStart = source.pollCursorAt().minus(overlap);
            try {
                transactionTemplate.executeWithoutResult(status -> jobRepository.saveAndFlush(SourceSyncJob.poll(
                        source.organizationId(),
                        source.projectId(),
                        source.id(),
                        windowStart,
                        now,
                        now
                )));
                log.info("Scheduled a poll of source {} from {}.", source.id(), windowStart);
            } catch (DataIntegrityViolationException exception) {
                // Another worker scheduled the same poll a moment ago.
                log.debug("A poll of source {} is already under way.", source.id());
            }
        }
    }
}
