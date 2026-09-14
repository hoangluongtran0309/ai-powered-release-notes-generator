package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.github.PullRequestFiles;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryAccess;
import com.hoangluongtran0309.releaseflow.project.GitHubRepositoryCredentials;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Collects the changed files of received pull requests and classifies them. Claims
 * and results are written in short transactions; the GitHub call runs between them.
 * A change whose files cannot be listed is still classified, with a trigger that
 * forces review.
 */
@Component
class ChangeProcessingWorker {

    static final int MAX_ATTEMPTS = 3;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String UNEXPECTED_ERROR = "unexpected_error";

    private static final Logger log = LoggerFactory.getLogger(ChangeProcessingWorker.class);

    private final ChangeProcessingJobRepository jobRepository;
    private final ChangeRepository changeRepository;
    private final GitHubRepositoryAccess repositoryAccess;
    private final GitHubApiClient gitHubApiClient;
    private final SensitivePathRules sensitivePaths;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    ChangeProcessingWorker(
            ChangeProcessingJobRepository jobRepository,
            ChangeRepository changeRepository,
            GitHubRepositoryAccess repositoryAccess,
            GitHubApiClient gitHubApiClient,
            SensitivePathRules sensitivePaths,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.processing.enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.changeRepository = changeRepository;
        this.repositoryAccess = repositoryAccess;
        this.gitHubApiClient = gitHubApiClient;
        this.sensitivePaths = sensitivePaths;
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
            log.error("Change processing stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Processes at most one due job.
     *
     * @return whether a job was claimed
     */
    boolean processOne() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            int recovered = jobRepository.recoverStale(now.minus(STALE_AFTER), now);
            if (recovered > 0) {
                log.warn("Recovered {} change processing job(s) whose worker stopped responding.", recovered);
            }
        });

        Optional<Claim> claim = transactionTemplate.execute(status -> jobRepository.lockNextDue(now)
                .map(job -> {
                    job.claim(now);
                    Change change = findChange(job);
                    return new Claim(job.getId(), job.getOrganizationId(), job.getProjectId(), job.getChangeId(),
                            job.getAttempts(), now, change.pullRequest());
                }));
        if (claim.isEmpty()) {
            return false;
        }
        try {
            process(claim.get());
        } catch (RuntimeException exception) {
            // The change stays visibly Processing and is tried again later, never skipped.
            log.error("Could not process change {}; retrying later.", claim.get().changeId(), exception);
            reschedule(claim.get(), UNEXPECTED_ERROR);
        }
        return true;
    }

    private void process(Claim claim) {
        PullRequestFiles files = collectFiles(claim);
        if (!files.isCollected() && files.retryable() && claim.attempt() < MAX_ATTEMPTS) {
            reschedule(claim, files.failure());
            return;
        }

        ChangeClassification classification = ChangeClassifier.classify(claim.pullRequest(), files, sensitivePaths);
        transactionTemplate.executeWithoutResult(status -> {
            ChangeProcessingJob job = jobRepository.findById(claim.jobId()).orElseThrow();
            if (!job.isClaimedAt(claim.claimedAt())) {
                // The claim went stale and another worker took the job over.
                return;
            }
            findChange(job).completeProcessing(files, classification);
            job.complete(files.failure(), now());
        });
        log.info("Processed change {} with {} changed file(s) {}.", claim.changeId(),
                files.isCollected() ? files.files().size() : 0,
                files.isCollected() ? "collected" : "unavailable (" + files.failure() + ")");
    }

    private PullRequestFiles collectFiles(Claim claim) {
        Optional<GitHubRepositoryCredentials> repository = repositoryAccess.find(claim.organizationId(), claim.projectId());
        Optional<String> token = repository.flatMap(GitHubRepositoryCredentials::accessToken);
        if (token.isEmpty()) {
            return PullRequestFiles.unavailable(PullRequestFiles.NO_ACCESS_TOKEN, false);
        }
        return gitHubApiClient.pullRequestFiles(
                repository.get().owner(),
                repository.get().repository(),
                claim.pullRequest().number(),
                token.get()
        );
    }

    private void reschedule(Claim claim, String error) {
        Instant retryAt = now().plus(backoff(claim.attempt()));
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .ifPresent(job -> job.retry(error, retryAt)));
        log.info("Retrying change {} at {} after attempt {} ({}).", claim.changeId(), retryAt, claim.attempt(), error);
    }

    private Change findChange(ChangeProcessingJob job) {
        return changeRepository.findByIdAndOrganizationIdAndProjectId(
                        job.getChangeId(),
                        job.getOrganizationId(),
                        job.getProjectId()
                )
                .orElseThrow();
    }

    // 2, 4, 8 … seconds, capped at five minutes.
    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(8, attempt)));
    }

    // PostgreSQL keeps microseconds, so a claim time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            UUID jobId,
            UUID organizationId,
            UUID projectId,
            UUID changeId,
            int attempt,
            Instant claimedAt,
            MergedPullRequest pullRequest
    ) {
    }
}
