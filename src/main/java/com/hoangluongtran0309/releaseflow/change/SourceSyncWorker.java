package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.IntegrationSourceService;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reads what a source has recorded, for the two reasons there are to ask: an administrator
 * requested its history, or a polled source's schedule came round. Each provider has its
 * own {@link SourceHistoryReader}, and everything it returns goes through
 * {@link ChangeIntake}, exactly like a webhook delivery. The provider is called outside any
 * transaction; the cursor and counts are saved after every page.
 */
@Component
class SourceSyncWorker {

    static final int MAX_ATTEMPTS = 5;
    static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    // A poll that gave up still comes back, because the schedule is the whole point.
    static final Duration POLL_RETRY_AFTER_FAILURE = Duration.ofMinutes(15);
    static final String NO_ACCESS_TOKEN = ChangedFiles.NO_ACCESS_TOKEN;
    static final String ACCESS_REJECTED = ChangedFiles.ACCESS_REJECTED;
    static final String RATE_LIMITED = "rate_limited";
    static final String INVALID_RESPONSE = ChangedFiles.INVALID_RESPONSE;
    static final String SOURCE_MISSING = "source_missing";
    static final String UNEXPECTED_ERROR = "unexpected_error";

    private static final Logger log = LoggerFactory.getLogger(SourceSyncWorker.class);

    private final SourceSyncJobRepository jobRepository;
    private final JiraPollScheduler pollScheduler;
    private final SourceAccess sourceAccess;
    private final SourceHistoryReaders readers;
    private final ChangeIntake intake;
    private final IntegrationSourceService sourceService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    SourceSyncWorker(
            SourceSyncJobRepository jobRepository,
            JiraPollScheduler pollScheduler,
            SourceAccess sourceAccess,
            SourceHistoryReaders readers,
            ChangeIntake intake,
            IntegrationSourceService sourceService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.processing.enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.pollScheduler = pollScheduler;
        this.sourceAccess = sourceAccess;
        this.readers = readers;
        this.intake = intake;
        this.sourceService = sourceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT1S")
    void processDueJobs() {
        if (!enabled) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining due jobs before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Source import stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one due job, page by page, until it finishes, stops at its limit, or
     * has to wait.
     *
     * @return whether a job was claimed
     */
    boolean processOne() {
        Instant now = now();
        // Nobody asks for a poll, so the schedule has to speak for itself first.
        pollScheduler.scheduleDuePolls(now);
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = jobRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} source import job(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> jobRepository.lockNextDue(now).map(job -> {
            job.claim(now);
            return new Claim(job.getId(), job.getOrganizationId(), job.getProjectId(), job.getSourceId(),
                    job.getType(), job.getWindowStart(), job.getWindowEnd(), job.getCursor(), job.getImportedCount(),
                    job.getItemLimit(), job.getAttempts(), now);
        }));
        if (claim.isEmpty()) {
            return false;
        }
        try {
            work(claim.get());
        } catch (RuntimeException exception) {
            log.error("Source import job {} failed unexpectedly.", claim.get().jobId(), exception);
            failOrRetry(claim.get(), UNEXPECTED_ERROR, null);
        }
        return true;
    }

    private void work(Claim claim) {
        Optional<SourceCredentials> credentials = sourceAccess
                .find(claim.organizationId(), claim.projectId(), claim.sourceId());
        if (credentials.isEmpty()) {
            fail(claim, SOURCE_MISSING, false);
            return;
        }
        Optional<String> token = credentials.get().accessToken();
        if (token.isEmpty()) {
            fail(claim, NO_ACCESS_TOKEN, false);
            return;
        }
        SourceHistoryReader reader = readers.of(credentials.get().sourceType());
        SyncCursor cursor = claim.cursor();
        int imported = claim.importedSoFar();
        while (true) {
            HistoryPage page = reader.read(
                    credentials.get(), token.get(), cursor.provider(), claim.windowStart(), claim.windowEnd());
            switch (page.status()) {
                case LISTED -> {
                    // Handled below.
                }
                case RATE_LIMITED -> {
                    failOrRetry(claim, RATE_LIMITED, page.retryAfter());
                    return;
                }
                case UNAVAILABLE -> {
                    failOrRetry(claim, reader.unavailableCode(), null);
                    return;
                }
                case REJECTED -> {
                    fail(claim, ACCESS_REJECTED, true);
                    return;
                }
                case INVALID_RESPONSE -> {
                    fail(claim, INVALID_RESPONSE, false);
                    return;
                }
            }

            Scan scan = readPage(claim, credentials.get(), reader, page, cursor, imported);
            imported += scan.imported();
            // A page the provider says is its last one ends the import.
            Ending ending = scan.ending() == Ending.MORE && page.lastPage() ? Ending.COMPLETE : scan.ending();
            if (!progress(claim, scan.next(), scan, ending)) {
                return;
            }
            if (ending != Ending.MORE) {
                log.info("Import job {} {} at {} ({} new change(s) this run).", claim.jobId(),
                        ending == Ending.LIMIT ? "stopped at its limit" : "finished", scan.next(), imported);
                return;
            }
            cursor = scan.next();
        }
    }

    // Handles the items of one page from the cursor's offset.
    private Scan readPage(
            Claim claim,
            SourceCredentials credentials,
            SourceHistoryReader reader,
            HistoryPage page,
            SyncCursor cursor,
            int importedBefore
    ) {
        List<HistoryItem> items = page.items();
        SyncCursor nextPage = cursor.nextPage(page.nextProviderCursor());
        int scanned = 0;
        int imported = 0;
        for (int index = cursor.offset(); index < items.size(); index++) {
            HistoryItem item = items.get(index);
            scanned++;
            // Only a provider asked for its newest updates first runs out of history this way.
            if (reader.endsAtOlderItems() && item.updatedAt() != null && item.updatedAt().isBefore(claim.windowStart())) {
                return new Scan(scanned, imported, cursor.at(index + 1), Ending.COMPLETE);
            }
            Instant mergedAt = item.mergedAt();
            if (mergedAt == null || mergedAt.isBefore(claim.windowStart()) || mergedAt.isAfter(claim.windowEnd())) {
                continue;
            }
            if (item.change() == null) {
                // One unreadable item does not stop the rest of the history.
                log.warn("Import job {} skipped {}: its fields could not be read.", claim.jobId(), item.label());
                continue;
            }
            ChangeIntake.Outcome outcome = intake.record(
                    claim.organizationId(), claim.projectId(), claim.sourceId(), credentials.sourceType(),
                    item.change(), ChangeOrigin.IMPORT, null);
            if (outcome == ChangeIntake.Outcome.RECORDED) {
                imported++;
                if (importedBefore + imported >= claim.itemLimit()) {
                    SyncCursor next = index + 1 < items.size() ? cursor.at(index + 1) : nextPage;
                    return new Scan(scanned, imported, next, Ending.LIMIT);
                }
            }
        }
        return new Scan(scanned, imported, nextPage, Ending.MORE);
    }

    // Saves the page's progress and, when the import ends, its outcome; false if the claim was lost.
    private boolean progress(Claim claim, SyncCursor next, Scan scan, Ending ending) {
        Boolean held = transactionTemplate.execute(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .map(job -> {
                    job.advance(next, scan.scanned(), scan.imported());
                    Instant now = now();
                    if (ending == Ending.COMPLETE) {
                        job.complete(now);
                        recordSuccess(claim, now);
                    } else if (ending == Ending.LIMIT) {
                        job.stopAtLimit(now);
                        recordSuccess(claim, now);
                    }
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(held);
    }

    // A poll books its next round; an import only records that it ran.
    private void recordSuccess(Claim claim, Instant now) {
        if (claim.jobType() == SourceSyncJob.Type.JIRA_POLL) {
            sourceService.recordPoll(claim.organizationId(), claim.sourceId(), claim.windowEnd(), now);
        } else {
            sourceService.recordSync(claim.organizationId(), claim.sourceId(), now, null, false);
        }
    }

    // A transient failure waits and tries again, as long as the provider said or with backoff; the fifth fails.
    private void failOrRetry(Claim claim, String error, Duration retryAfter) {
        if (claim.attempt() >= MAX_ATTEMPTS) {
            fail(claim, error, false);
            return;
        }
        Duration wait = retryAfter != null
                ? (retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter)
                : backoff(claim.attempt());
        Instant retryAt = now().plus(wait);
        update(claim, job -> job.retryLater(error, retryAt));
        if (claim.jobType() == SourceSyncJob.Type.JIRA_POLL) {
            // The cursor stays where it was, so the next attempt misses nothing.
            sourceService.recordPollFailure(claim.organizationId(), claim.sourceId(), error, retryAt, now());
        }
        log.info("Retrying import job {} at {} after attempt {} ({}).", claim.jobId(), retryAt, claim.attempt(), error);
    }

    private void fail(Claim claim, String error, boolean credentialRejected) {
        update(claim, job -> {
            Instant now = now();
            job.fail(error, now);
            if (claim.jobType() == SourceSyncJob.Type.JIRA_POLL) {
                sourceService.recordPollFailure(claim.organizationId(), claim.sourceId(), error,
                        now.plus(POLL_RETRY_AFTER_FAILURE), now);
            } else {
                sourceService.recordSync(claim.organizationId(), claim.sourceId(), now, error, credentialRejected);
            }
        });
        log.warn("Import job {} failed ({}).", claim.jobId(), error);
    }

    private void update(Claim claim, Consumer<SourceSyncJob> change) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(change));
    }

    // 2, 4, 8 … seconds, capped at five minutes.
    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(8, attempt)));
    }

    // PostgreSQL keeps microseconds, so a claim time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private enum Ending {
        MORE,
        COMPLETE,
        LIMIT
    }

    private record Scan(int scanned, int imported, SyncCursor next, Ending ending) {
    }

    private record Claim(
            UUID jobId,
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            SourceSyncJob.Type jobType,
            Instant windowStart,
            Instant windowEnd,
            SyncCursor cursor,
            int importedSoFar,
            int itemLimit,
            int attempt,
            Instant claimedAt
    ) {
    }
}
