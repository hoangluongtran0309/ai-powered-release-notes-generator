package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the run history and carries out what a person asks of it: running a rule by
 * hand, sending a failed action again, or calling a run off. Nothing here talks to a
 * provider; the worker does that.
 */
@Service
public class AutomationRunService {

    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRuleService ruleService;
    private final AutomationRunFactory runFactory;
    private final ReleaseAccess releaseAccess;
    private final Clock clock;

    AutomationRunService(
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationRuleService ruleService,
            AutomationRunFactory runFactory,
            ReleaseAccess releaseAccess,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.ruleService = ruleService;
        this.runFactory = runFactory;
        this.releaseAccess = releaseAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AutomationRunPage list(UUID organizationId, int page, int size) {
        AutomationRunPage.requireValid(page, size);
        List<AutomationRun> runs = runRepository.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(
                organizationId, PageRequest.of(page, size));
        return new AutomationRunPage(
                views(runs),
                page,
                size,
                runRepository.countByOrganizationId(organizationId)
        );
    }

    @Transactional(readOnly = true)
    public AutomationRunView get(UUID organizationId, UUID runId) {
        return views(List.of(find(organizationId, runId))).getFirst();
    }

    /**
     * Runs a rule against one published release. The request ID is the promise that a
     * repeat is the same run, so a retried click never delivers the note twice.
     */
    @Transactional
    public AutomationRunView execute(ReleaseFlowPrincipal principal, UUID ruleId, ExecuteRuleRequest request) {
        if (request.getRequestId() == null) {
            throw AutomationActionInvalidException.requestIdRequired();
        }
        AutomationRule rule = ruleService.require(principal.organizationId(), ruleId);
        return runRepository.findByRuleIdAndRequestId(ruleId, request.getRequestId())
                .map(existing -> views(List.of(existing)).getFirst())
                .orElseGet(() -> {
                    ReleaseAccess.ReleaseSnapshot release = releaseAccess
                            .find(principal.organizationId(), request.getReleaseId())
                            .orElseThrow(AutomationConflictException::releaseNotPublished);
                    if (release.status() != ReleaseStatus.PUBLISHED) {
                        throw AutomationConflictException.releaseNotPublished();
                    }
                    if (!rule.matches(release.projectId(), TriggerType.MANUAL)) {
                        throw AutomationConflictException.ruleUnavailable();
                    }
                    AutomationRun run = runFactory.create(
                            rule,
                            release,
                            TriggerType.MANUAL,
                            request.getRequestId(),
                            principal.userId(),
                            principal.displayName(),
                            null
                    );
                    return views(List.of(run)).getFirst();
                });
    }

    /**
     * Sends the first action that failed, or whose outcome nobody knows, once more.
     * Actions that already succeeded are never repeated.
     */
    @Transactional
    public AutomationRunView retry(UUID organizationId, UUID runId, boolean confirmUnknown) {
        AutomationRun run = find(organizationId, runId);
        if (run.getStatus() == ExecutionStatus.UNKNOWN && !confirmUnknown) {
            throw AutomationConflictException.unknownNeedsConfirmation();
        }
        if (run.getStatus() != ExecutionStatus.FAILED && run.getStatus() != ExecutionStatus.UNKNOWN) {
            throw AutomationConflictException.runNotRetryable();
        }
        List<AutomationActionRun> actions = actionRunRepository.findAllByRunIdOrderByPositionAsc(runId);
        actions.stream()
                .filter(action -> action.getStatus() == ExecutionStatus.FAILED
                        || action.getStatus() == ExecutionStatus.UNKNOWN)
                .min(Comparator.comparingInt(AutomationActionRun::getPosition))
                .orElseThrow(AutomationConflictException::runNotRetryable)
                .retry();
        // Everything the stopped action held up waits again with it.
        actions.stream()
                .filter(action -> action.getStatus() == ExecutionStatus.CANCELLED)
                .forEach(AutomationActionRun::retry);
        run.reopen();
        runRepository.flush();
        actionRunRepository.flush();
        return views(List.of(run)).getFirst();
    }

    /**
     * Calls a run off. An action already on its way finishes and records its result;
     * everything behind it is cancelled.
     */
    @Transactional
    public AutomationRunView cancel(UUID organizationId, UUID runId) {
        AutomationRun run = find(organizationId, runId);
        if (run.getStatus() == ExecutionStatus.SUCCEEDED || run.getStatus() == ExecutionStatus.CANCELLED) {
            return views(List.of(run)).getFirst();
        }
        Instant now = now();
        run.requestCancellation();
        List<AutomationActionRun> actions = actionRunRepository.findAllByRunIdOrderByPositionAsc(runId);
        if (actions.stream().noneMatch(action -> action.getStatus() == ExecutionStatus.RUNNING)) {
            actions.forEach(action -> action.cancelPending(now));
            run.settle(actions, now);
        }
        runRepository.flush();
        actionRunRepository.flush();
        return views(List.of(run)).getFirst();
    }

    private AutomationRun find(UUID organizationId, UUID runId) {
        return runRepository.findByIdAndOrganizationId(runId, organizationId)
                .orElseThrow(AutomationRunNotFoundException::new);
    }

    private List<AutomationRunView> views(List<AutomationRun> runs) {
        if (runs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AutomationActionRun>> byRun = actionRunRepository
                .findAllByRunIdInOrderByRunIdAscPositionAsc(runs.stream().map(AutomationRun::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(AutomationActionRun::getRunId));
        return runs.stream()
                .map(run -> new AutomationRunView(
                        run.getId(),
                        run.getRuleId(),
                        run.getRuleNameSnapshot(),
                        run.getReleaseId(),
                        run.getReleaseVersionSnapshot(),
                        run.getTriggerType(),
                        run.getRequestId(),
                        run.getInitiatorName(),
                        run.getStatus(),
                        run.isCancellationRequested(),
                        run.getCreatedAt(),
                        run.getStartedAt(),
                        run.getCompletedAt(),
                        byRun.getOrDefault(run.getId(), List.of()).stream()
                                .map(AutomationRunService::view)
                                .toList()
                ))
                .toList();
    }

    private static AutomationActionRunView view(AutomationActionRun action) {
        return new AutomationActionRunView(
                action.getId(),
                action.getPosition(),
                action.getActionType(),
                action.getAudienceNameSnapshot(),
                action.getLanguageSnapshot(),
                action.getStatus(),
                action.getExternalReference(),
                action.getErrorCode(),
                action.getAttempts(),
                action.getStartedAt(),
                action.getCompletedAt()
        );
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
