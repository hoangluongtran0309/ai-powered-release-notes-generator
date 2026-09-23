package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.release.AudienceNoteView;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import com.hoangluongtran0309.releaseflow.translation.TranslationState;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a Rule and a release into a Run whose Actions carry everything the deliveries
 * need. It only reads and writes the database, so it is safe inside the transaction
 * that drains the outbox; the worker performs the deliveries afterwards.
 *
 * <p>An Action whose audience and language have no ready note is written all the same,
 * without one. It fails when the worker reaches it, which is the point: a Rule nobody
 * can carry out must not stop the release that triggered it.
 */
@Component
class AutomationRunFactory {

    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRuleActionRepository ruleActionRepository;
    private final SourceAccess sourceAccess;
    private final Clock clock;

    AutomationRunFactory(
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationRuleActionRepository ruleActionRepository,
            SourceAccess sourceAccess,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.ruleActionRepository = ruleActionRepository;
        this.sourceAccess = sourceAccess;
        this.clock = clock;
    }

    AutomationRun create(
            AutomationRule rule,
            ReleaseAccess.ReleaseSnapshot release,
            TriggerType trigger,
            UUID requestId,
            UUID initiatedBy,
            String initiatorName,
            Instant scheduledFor
    ) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AutomationRun run = runRepository.saveAndFlush(new AutomationRun(
                UUID.randomUUID(),
                rule,
                release.projectId(),
                release.releaseId(),
                release.version(),
                trigger,
                requestId,
                initiatedBy,
                initiatorName,
                scheduledFor,
                now
        ));
        List<AutomationRuleAction> actions = ruleActionRepository.findAllByRuleIdOrderByPositionAsc(rule.getId());
        List<AutomationActionRun> actionRuns = actions.stream()
                .map(action -> actionRun(run, action, release))
                .toList();
        actionRunRepository.saveAllAndFlush(actionRuns);
        return run;
    }

    private AutomationActionRun actionRun(
            AutomationRun run,
            AutomationRuleAction action,
            ReleaseAccess.ReleaseSnapshot release
    ) {
        Optional<AudienceNoteView> note = release.notes().stream()
                .filter(candidate -> candidate.audienceId().equals(action.getAudienceId()))
                .filter(candidate -> candidate.language().equals(action.getTargetLanguage()))
                .filter(candidate -> candidate.translationStatus() == TranslationState.Status.READY)
                .findFirst();
        return new AutomationActionRun(
                UUID.randomUUID(),
                run.getId(),
                run.getOrganizationId(),
                action.getId(),
                action.getPosition(),
                action.getActionType(),
                action.getAudienceId(),
                note.map(AudienceNoteView::audienceName).orElse(action.getAudienceId().toString()),
                action.getTargetLanguage(),
                note.map(AudienceNoteView::content).orElse(null),
                configuration(action, release),
                action.getSecret()
        );
    }

    // A GitHub Release Action needs to know which repository the release belongs in.
    // Without exactly one GitHub source there is no answer, so the Action carries none
    // and fails when it runs.
    private Map<String, String> configuration(AutomationRuleAction action, ReleaseAccess.ReleaseSnapshot release) {
        Map<String, String> configuration = new LinkedHashMap<>(action.getConfiguration());
        if (action.getActionType() == ActionType.GITHUB_RELEASE) {
            sourceAccess.findSole(action.getOrganizationId(), release.projectId(), SourceType.GITHUB)
                    .ifPresent(source -> {
                        configuration.put(
                                GitHubReleaseActionExecutor.REPOSITORY,
                                source.repositoryOwner() + "/" + source.repositoryName()
                        );
                    });
        }
        return configuration;
    }
}
