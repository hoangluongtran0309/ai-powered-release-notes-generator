package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns published releases into runs and carries out one delivery at a time. A claim
 * and a result are each written in a short transaction; the provider is called between
 * them, with nothing open.
 *
 * <p>Deliveries cannot be repeated safely, so this worker never retries by itself. An
 * action whose worker stopped becomes {@link ExecutionStatus#UNKNOWN} after
 * {@value #STALE_AFTER_MINUTES} minutes and waits for a person to decide.
 */
@Component
class AutomationWorker {

    static final int STALE_AFTER_MINUTES = 5;
    static final Duration STALE_AFTER = Duration.ofMinutes(STALE_AFTER_MINUTES);
    static final Duration OUTBOX_STALE_AFTER = Duration.ofMinutes(10);
    static final int MAX_OUTBOX_ATTEMPTS = 3;
    static final Duration OUTBOX_RETRY_AFTER = Duration.ofMinutes(1);
    static final String UNEXPECTED_ERROR = "unexpected_error";
    static final String RELEASE_UNAVAILABLE = "release_unavailable";

    private static final Logger log = LoggerFactory.getLogger(AutomationWorker.class);

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationPublishJobRepository publishJobRepository;
    private final AutomationRunFactory runFactory;
    private final AutomationSecrets secrets;
    private final ReleaseAccess releaseAccess;
    private final Map<ActionType, RuleActionExecutor> executors;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;

    AutomationWorker(
            AutomationRuleRepository ruleRepository,
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationPublishJobRepository publishJobRepository,
            AutomationRunFactory runFactory,
            AutomationSecrets secrets,
            ReleaseAccess releaseAccess,
            List<RuleActionExecutor> executors,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.automation.worker-enabled}") boolean enabled
    ) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.publishJobRepository = publishJobRepository;
        this.runFactory = runFactory;
        this.secrets = secrets;
        this.releaseAccess = releaseAccess;
        this.executors = executors.stream().collect(Collectors.toMap(
                RuleActionExecutor::actionType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Two executors claim " + first.actionType() + ".");
                },
                () -> new EnumMap<>(ActionType.class)
        ));
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "PT1S")
    void processDueWork() {
        if (!enabled) {
            return;
        }
        try {
            while (processOne()) {
                // Keep draining before sleeping again.
            }
        } catch (RuntimeException exception) {
            log.error("Automation stopped unexpectedly; it resumes on the next tick.", exception);
        }
    }

    /**
     * Works at most one thing: a publication waiting to become runs, or one delivery.
     *
     * @return whether there was something to do
     */
    boolean processOne() {
        recoverStale();
        if (createDueRuns()) {
            return true;
        }
        Optional<Claim> claim = transactionTemplate.execute(status -> claimNext());
        if (claim == null || claim.isEmpty()) {
            return false;
        }
        finish(claim.get(), execute(claim.get()));
        return true;
    }

    // An Action nobody finished may already have been delivered, so it is never simply
    // offered again: it becomes UNKNOWN, and its Run with it.
    private void recoverStale() {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> {
            publishJobRepository.recoverStale(now.minus(OUTBOX_STALE_AFTER), now);
            List<UUID> stale = actionRunRepository.findStaleRunningIds(now.minus(STALE_AFTER));
            if (stale.isEmpty()) {
                return;
            }
            log.warn("Recovering {} automation action(s) whose worker stopped responding.", stale.size());
            actionRunRepository.findAllById(stale).forEach(action -> {
                action.markUnknown(ActionResult.OUTCOME_UNKNOWN, now);
                runRepository.findById(action.getRunId()).ifPresent(run -> run.settle(
                        actionRunRepository.findAllByRunIdOrderByPositionAsc(run.getId()), now));
            });
            actionRunRepository.flush();
            runRepository.flush();
        });
    }

    /**
     * Turns the next published release into one Run per matching Rule. Creating Runs
     * is only database work, and it is idempotent per Rule and Release, so a claim
     * that is lost or rolled back simply happens again, at most
     * {@value #MAX_OUTBOX_ATTEMPTS} times.
     */
    private boolean createDueRuns() {
        Optional<OutboxClaim> claim = transactionTemplate.execute(status ->
                publishJobRepository.lockNextDue(now()).map(job -> {
                    job.claim(now());
                    publishJobRepository.flush();
                    return new OutboxClaim(
                            job.getId(), job.getOrganizationId(), job.getReleaseId(), job.getAttempts());
                }));
        if (claim == null || claim.isEmpty()) {
            return false;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> createRuns(claim.get()));
        } catch (RuntimeException exception) {
            log.error("Could not create the automation runs of release {}.", claim.get().releaseId(), exception);
            completeOutbox(claim.get(), job -> {
                if (claim.get().attempt() >= MAX_OUTBOX_ATTEMPTS) {
                    job.fail(UNEXPECTED_ERROR, now());
                } else {
                    job.retryLater(UNEXPECTED_ERROR, now().plus(OUTBOX_RETRY_AFTER));
                }
            });
        }
        return true;
    }

    private void createRuns(OutboxClaim claim) {
        Optional<ReleaseAccess.ReleaseSnapshot> release =
                releaseAccess.find(claim.organizationId(), claim.releaseId());
        if (release.isEmpty() || release.get().status() != ReleaseStatus.PUBLISHED) {
            completeOutbox(claim, job -> job.fail(RELEASE_UNAVAILABLE, now()));
            return;
        }
        for (AutomationRule rule : ruleRepository
                .findAllByOrganizationIdAndTriggerTypeAndEnabledTrueAndActiveTrueOrderByCreatedAtAscIdAsc(
                        claim.organizationId(), TriggerType.RELEASE_PUBLISHED)) {
            if (!rule.matches(release.get().projectId(), TriggerType.RELEASE_PUBLISHED)
                    || runRepository.existsByRuleIdAndReleaseIdAndTriggerType(
                            rule.getId(), claim.releaseId(), TriggerType.RELEASE_PUBLISHED)) {
                continue;
            }
            runFactory.create(rule, release.get(), TriggerType.RELEASE_PUBLISHED, null, null, null, null);
        }
        completeOutbox(claim, job -> job.succeed(now()));
    }

    private void completeOutbox(OutboxClaim claim, Consumer<AutomationPublishJob> result) {
        transactionTemplate.executeWithoutResult(status -> publishJobRepository.findById(claim.jobId())
                .ifPresent(job -> {
                    result.accept(job);
                    publishJobRepository.flush();
                }));
    }

    private Optional<Claim> claimNext() {
        Instant now = now();
        return actionRunRepository.lockNextPending().map(action -> {
            action.claim(now);
            AutomationRun run = runRepository.findById(action.getRunId()).orElseThrow();
            run.actionClaimed(now);
            actionRunRepository.flush();
            runRepository.flush();
            return new Claim(
                    action.getId(),
                    action.getOrganizationId(),
                    run.getProjectId(),
                    run.getReleaseId(),
                    run.getReleaseVersionSnapshot(),
                    action.getActionType(),
                    action.getNoteContentSnapshot(),
                    action.getConfigurationSnapshot(),
                    secrets.decrypt(
                            action.getSecretSnapshot(),
                            action.getOrganizationId(),
                            run.getRuleId(),
                            action.getRuleActionId(),
                            action.getActionType()
                    ),
                    now
            );
        });
    }

    private ActionResult execute(Claim claim) {
        if (claim.noteContent() == null) {
            return ActionResult.failed(ActionResult.NOTE_MISSING);
        }
        RuleActionExecutor executor = executors.get(claim.actionType());
        if (executor == null) {
            return ActionResult.failed(ActionResult.ACTION_UNSUPPORTED);
        }
        try {
            return executor.execute(new ActionCommand(
                    claim.actionRunId(),
                    claim.organizationId(),
                    claim.projectId(),
                    claim.releaseId(),
                    claim.releaseVersion(),
                    claim.noteContent(),
                    claim.configuration(),
                    claim.rawSecret()
            ));
        } catch (RuntimeException exception) {
            // Nobody knows whether the provider acted, so nobody may repeat it alone.
            log.error("Automation action {} ended without a result.", claim.actionRunId(), exception);
            return ActionResult.unknown(UNEXPECTED_ERROR);
        }
    }

    // Applies the result if this worker still holds the claim, then advances the Run.
    private void finish(Claim claim, ActionResult result) {
        Instant now = now();
        transactionTemplate.executeWithoutResult(status -> actionRunRepository.findById(claim.actionRunId())
                .filter(action -> action.isClaimedAt(claim.claimedAt()))
                .ifPresent(action -> {
                    switch (result.outcome()) {
                        case SUCCEEDED -> action.succeed(result.externalReference(), now);
                        case FAILED -> action.fail(result.errorCode(), now);
                        default -> action.markUnknown(result.errorCode(), now);
                    }
                    actionRunRepository.flush();
                    runRepository.findById(action.getRunId()).ifPresent(run -> {
                        List<AutomationActionRun> actions =
                                actionRunRepository.findAllByRunIdOrderByPositionAsc(run.getId());
                        if (run.isCancellationRequested()) {
                            actions.forEach(pending -> pending.cancelPending(now));
                        }
                        run.settle(actions, now);
                        runRepository.flush();
                        actionRunRepository.flush();
                    });
                }));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record OutboxClaim(UUID jobId, UUID organizationId, UUID releaseId, int attempt) {
    }

    private record Claim(
            UUID actionRunId,
            UUID organizationId,
            UUID projectId,
            UUID releaseId,
            String releaseVersion,
            ActionType actionType,
            String noteContent,
            Map<String, String> configuration,
            String rawSecret,
            Instant claimedAt
    ) {
    }
}
