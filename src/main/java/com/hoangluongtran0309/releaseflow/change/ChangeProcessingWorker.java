package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategoryService;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Collects the changed files of received pull requests, asks the configured AI once,
 * and classifies them. Claims and results are written in short transactions; GitHub
 * and the AI are called between them. A change whose files or AI answer cannot be
 * obtained is still classified, with a trigger that forces review.
 */
@Component
class ChangeProcessingWorker {

    static final int MAX_ATTEMPTS = 3;
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String UNEXPECTED_ERROR = "unexpected_error";
    static final String AI_FAILED = "ai_failed";
    static final String AI_DID_NOT_FINISH = "ai_did_not_finish";

    private static final Logger log = LoggerFactory.getLogger(ChangeProcessingWorker.class);

    private final ChangeProcessingJobRepository jobRepository;
    private final ChangeRepository changeRepository;
    private final GitHubRepositoryAccess repositoryAccess;
    private final GitHubApiClient gitHubApiClient;
    private final ProjectSensitivePathService sensitivePaths;
    private final AiClassifiers aiClassifiers;
    private final OutputLanguageService outputLanguageService;
    private final AudienceService audienceService;
    private final CategoryService categoryService;
    private final CategorySuggestionService suggestionService;
    private final ClassificationSettings settings;
    private final DuplicateDetector duplicateDetector;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    ChangeProcessingWorker(
            ChangeProcessingJobRepository jobRepository,
            ChangeRepository changeRepository,
            GitHubRepositoryAccess repositoryAccess,
            GitHubApiClient gitHubApiClient,
            ProjectSensitivePathService sensitivePaths,
            AiClassifiers aiClassifiers,
            OutputLanguageService outputLanguageService,
            AudienceService audienceService,
            CategoryService categoryService,
            CategorySuggestionService suggestionService,
            ClassificationSettings settings,
            DuplicateDetector duplicateDetector,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.processing.enabled}") boolean enabled
    ) {
        this.jobRepository = jobRepository;
        this.changeRepository = changeRepository;
        this.repositoryAccess = repositoryAccess;
        this.gitHubApiClient = gitHubApiClient;
        this.sensitivePaths = sensitivePaths;
        this.aiClassifiers = aiClassifiers;
        this.outputLanguageService = outputLanguageService;
        this.audienceService = audienceService;
        this.categoryService = categoryService;
        this.suggestionService = suggestionService;
        this.settings = settings;
        this.duplicateDetector = duplicateDetector;
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
                    Change change = findChange(job);
                    if (job.isFallbackRequired()) {
                        completeWithoutAi(job, change, now);
                        return Claim.FINISHED;
                    }
                    job.claim(now);
                    return new Claim(job.getId(), job.getOrganizationId(), job.getProjectId(), change.getSourceId(),
                            job.getChangeId(), job.getAttempts(), now, change.pullRequest());
                }));
        if (claim.isEmpty()) {
            return false;
        }
        if (claim.get() == Claim.FINISHED) {
            return true;
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
        List<CategoryRef> catalog = categoryService.active(claim.organizationId());
        ChangeClassification rules = ChangeClassifier.classify(claim.pullRequest(), files,
                sensitivePaths.forProject(claim.organizationId(), claim.projectId()), catalog);

        Optional<AiChangeClassifier> ai = aiClassifiers.active();
        if (ai.isEmpty()) {
            complete(claim, files, ChangeAiMerge.merge(rules, null, null), files.failure());
            return;
        }

        Boolean classifying = transactionTemplate.execute(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .map(job -> {
                    findChange(job).recordChangedFiles(files);
                    job.startClassifying();
                    return true;
                })
                .orElse(false));
        if (!Boolean.TRUE.equals(classifying)) {
            return;
        }

        AiOutcome outcome = askAi(ai.get(), claim, rules.category(), catalog);
        complete(
                claim,
                files,
                ChangeAiMerge.merge(rules, outcome, claim.pullRequest(), settings.contextThreshold()),
                outcome.succeeded() ? files.failure() : AI_FAILED
        );
    }

    // Exactly one request per change: a failure becomes a fallback, never a retry.
    private AiOutcome askAi(AiChangeClassifier ai, Claim claim, CategoryRef rulesCategory, List<CategoryRef> catalog) {
        OutputLanguage language = outputLanguageService.outputLanguage(claim.organizationId());
        List<AudienceBrief> audiences = audienceService.briefs(claim.organizationId());
        try {
            AiClassification answer = ai.classify(AiClassificationRequest.of(
                    claim.changeId(),
                    claim.pullRequest(),
                    language,
                    rulesCategory,
                    catalog,
                    audiences,
                    settings.contextThreshold()
            ));
            return AiOutcome.succeeded(ai, answer, language);
        } catch (AiClassificationException exception) {
            return AiOutcome.failed(ai, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("AI classification of change {} failed unexpectedly.", claim.changeId(), exception);
            return AiOutcome.failed(ai, "AI classification failed unexpectedly.");
        }
    }

    private void complete(Claim claim, PullRequestFiles files, ChangeAiMerge.ClassifiedChange outcome, String error) {
        transactionTemplate.executeWithoutResult(status -> {
            ChangeProcessingJob job = jobRepository.findById(claim.jobId()).orElseThrow();
            if (!job.isClaimedAt(claim.claimedAt())) {
                // The claim went stale and another worker took the job over.
                return;
            }
            Instant now = now();
            Change change = findChange(job);
            change.completeProcessing(files, outcome, now);
            duplicateDetector.detect(change);
            job.complete(error, now);
            if (outcome.suggestion() != null) {
                suggestionService.propose(claim.organizationId(), claim.projectId(), claim.changeId(), outcome.suggestion());
            }
        });
        log.info("Processed change {} with {} changed file(s) {}{}.", claim.changeId(),
                files.isCollected() ? files.files().size() : 0,
                files.isCollected() ? "collected" : "unavailable (" + files.failure() + ")",
                outcome.ai() == null ? "" : outcome.ai().succeeded() ? " and an AI summary" : " and an AI failure");
    }

    // A job whose AI call may already have happened is finished from the recorded files.
    private void completeWithoutAi(ChangeProcessingJob job, Change change, Instant now) {
        PullRequestFiles files = change.recordedFiles();
        ChangeClassification rules = ChangeClassifier.classify(change.pullRequest(), files,
                sensitivePaths.forProject(change.getOrganizationId(), change.getProjectId()),
                categoryService.active(change.getOrganizationId()));
        AiOutcome outcome = aiClassifiers.active()
                .map(ai -> AiOutcome.failed(ai, AiOutcome.DID_NOT_FINISH))
                .orElse(null);
        change.completeProcessing(files, ChangeAiMerge.merge(rules, outcome, null), now);
        duplicateDetector.detect(change);
        job.complete(AI_DID_NOT_FINISH, now);
        log.warn("Completed change {} without AI because its classification did not finish.", change.getId());
    }

    private PullRequestFiles collectFiles(Claim claim) {
        Optional<GitHubRepositoryCredentials> repository = repositoryAccess.find(claim.organizationId(), claim.projectId(),
                claim.sourceId());
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

    // Only a job still collecting files is retried; once the AI may have been asked, it never is.
    private void reschedule(Claim claim, String error) {
        Instant retryAt = now().plus(backoff(claim.attempt()));
        transactionTemplate.executeWithoutResult(status -> jobRepository.findById(claim.jobId())
                .filter(job -> job.isClaimedAt(claim.claimedAt()))
                .filter(job -> job.getStatus() == ChangeProcessingJob.Status.ENRICHING)
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
            UUID sourceId,
            UUID changeId,
            int attempt,
            Instant claimedAt,
            MergedPullRequest pullRequest
    ) {
        // Marks a job that was completed while claiming it.
        static final Claim FINISHED = new Claim(null, null, null, null, null, 0, null, null);
    }
}
