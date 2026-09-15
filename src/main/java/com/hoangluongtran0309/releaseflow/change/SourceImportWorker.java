package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.github.PullRequestListing;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryAccess;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryCredentials;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reads a source's history for due import jobs. GitHub lists closed pull requests most
 * recently updated first, so the import stops at the first one updated before its
 * window. Each merged pull request in the window goes through {@link ChangeIntake},
 * exactly like a webhook delivery. GitHub is called outside any transaction; the cursor
 * and counts are saved after every page.
 */
@Component
class SourceImportWorker {

    static final int MAX_ATTEMPTS = 5;
    static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String NO_ACCESS_TOKEN = "no_access_token";
    static final String ACCESS_REJECTED = "access_rejected";
    static final String RATE_LIMITED = "rate_limited";
    static final String UNAVAILABLE = "github_unavailable";
    static final String INVALID_RESPONSE = "invalid_response";
    static final String UNEXPECTED_ERROR = "unexpected_error";

    private static final Logger log = LoggerFactory.getLogger(SourceImportWorker.class);

    private final SourceSyncJobRepository jobRepository;
    private final GitHubRepositoryAccess repositoryAccess;
    private final GitHubApiClient gitHubApiClient;
    private final ChangeIntake intake;
    private final IntegrationSourceService sourceService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    SourceImportWorker(
            SourceSyncJobRepository jobRepository,
            GitHubRepositoryAccess repositoryAccess,
            GitHubApiClient gitHubApiClient,
            ChangeIntake intake,
            IntegrationSourceService sourceService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.processing.enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.repositoryAccess = repositoryAccess;
        this.gitHubApiClient = gitHubApiClient;
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
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = jobRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} source import job(s) whose worker stopped responding.", recovered);
            }
        });
        Optional<Claim> claim = transactionTemplate.execute(status -> jobRepository.lockNextDue(now).map(job -> {
            job.claim(now);
            return new Claim(job.getId(), job.getOrganizationId(), job.getProjectId(), job.getSourceId(),
                    job.getWindowStart(), job.getWindowEnd(), job.getCursor(), job.getImportedCount(),
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
        Optional<GitHubRepositoryCredentials> repository = repositoryAccess
                .find(claim.organizationId(), claim.projectId(), claim.sourceId());
        Optional<String> token = repository.flatMap(GitHubRepositoryCredentials::accessToken);
        if (token.isEmpty()) {
            fail(claim, NO_ACCESS_TOKEN, false);
            return;
        }
        ImportCursor cursor = claim.cursor();
        int imported = claim.importedSoFar();
        while (true) {
            PullRequestListing listing = gitHubApiClient.closedPullRequests(
                    repository.get().owner(), repository.get().repository(), cursor.page(), token.get());
            switch (listing.status()) {
                case LISTED -> {
                    // Handled below.
                }
                case RATE_LIMITED -> {
                    failOrRetry(claim, RATE_LIMITED, listing.retryAfter());
                    return;
                }
                case UNAVAILABLE -> {
                    failOrRetry(claim, UNAVAILABLE, null);
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

            Page page = readPage(claim, listing.pullRequests(), cursor, imported);
            imported += page.imported();
            // A page shorter than a full one is the last.
            Ending ending = page.ending() == Ending.MORE && listing.lastPage() ? Ending.COMPLETE : page.ending();
            if (!progress(claim, page.next(), page, ending)) {
                return;
            }
            if (ending != Ending.MORE) {
                log.info("Import job {} {} at {} ({} new change(s) this run).", claim.jobId(),
                        ending == Ending.LIMIT ? "stopped at its limit" : "finished", page.next(), imported);
                return;
            }
            cursor = page.next();
        }
    }

    // Handles the pull requests of one page from the cursor's offset.
    private Page readPage(Claim claim, List<JsonNode> pullRequests, ImportCursor cursor, int importedBefore) {
        int scanned = 0;
        int imported = 0;
        for (int index = cursor.offset(); index < pullRequests.size(); index++) {
            JsonNode pullRequest = pullRequests.get(index);
            scanned++;
            Instant updatedAt = instant(pullRequest.path("updated_at"));
            if (updatedAt != null && updatedAt.isBefore(claim.windowStart())) {
                return new Page(scanned, imported, cursor.at(index + 1), Ending.COMPLETE);
            }
            Instant mergedAt = instant(pullRequest.path("merged_at"));
            if (mergedAt == null || mergedAt.isBefore(claim.windowStart()) || mergedAt.isAfter(claim.windowEnd())) {
                continue;
            }
            final MergedPullRequest merged;
            try {
                merged = MergedPullRequest.from(pullRequest);
            } catch (MalformedWebhookPayloadException exception) {
                // One unreadable pull request does not stop the rest of the history.
                log.warn("Import job {} skipped pull request #{}: {}", claim.jobId(),
                        pullRequest.path("number").asString("?"), exception.getMessage());
                continue;
            }
            ChangeIntake.Outcome outcome = intake.record(
                    claim.organizationId(), claim.projectId(), claim.sourceId(), merged, ChangeOrigin.IMPORT, null);
            if (outcome == ChangeIntake.Outcome.RECORDED) {
                imported++;
                if (importedBefore + imported >= claim.itemLimit()) {
                    ImportCursor next = index + 1 < pullRequests.size() ? cursor.at(index + 1) : cursor.nextPage();
                    return new Page(scanned, imported, next, Ending.LIMIT);
                }
            }
        }
        return new Page(scanned, imported, cursor.nextPage(), Ending.MORE);
    }

    // Saves the page's progress and, when the import ends, its outcome; false if the claim was lost.
    private boolean progress(Claim claim, ImportCursor next, Page page, Ending ending) {
        Boolean held = transactionTemplate.execute(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .map(job -> {
                    job.advance(next, page.scanned(), page.imported());
                    Instant now = now();
                    if (ending == Ending.COMPLETE) {
                        job.complete(now);
                        sourceService.recordSync(claim.organizationId(), claim.sourceId(), now, null, false);
                    } else if (ending == Ending.LIMIT) {
                        job.stopAtLimit(now);
                        sourceService.recordSync(claim.organizationId(), claim.sourceId(), now, null, false);
                    }
                    return true;
                })
                .orElse(false));
        return Boolean.TRUE.equals(held);
    }

    // A transient failure waits and tries again, as long as GitHub said or with backoff; the fifth fails.
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
        log.info("Retrying import job {} at {} after attempt {} ({}).", claim.jobId(), retryAt, claim.attempt(), error);
    }

    private void fail(Claim claim, String error, boolean credentialRejected) {
        update(claim, job -> {
            Instant now = now();
            job.fail(error, now);
            sourceService.recordSync(claim.organizationId(), claim.sourceId(), now, error, credentialRejected);
        });
        log.warn("Import job {} failed ({}).", claim.jobId(), error);
    }

    private void update(Claim claim, Consumer<SourceSyncJob> change) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(change));
    }

    private static Instant instant(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        try {
            return Instant.parse(node.stringValue());
        } catch (DateTimeParseException exception) {
            return null;
        }
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

    private record Page(int scanned, int imported, ImportCursor next, Ending ending) {
    }

    private record Claim(
            UUID jobId,
            UUID organizationId,
            UUID projectId,
            UUID sourceId,
            Instant windowStart,
            Instant windowEnd,
            ImportCursor cursor,
            int importedSoFar,
            int itemLimit,
            int attempt,
            Instant claimedAt
    ) {
    }
}
