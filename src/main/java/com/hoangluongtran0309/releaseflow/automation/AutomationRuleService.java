package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.audience.AudienceNotFoundException;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.AudienceView;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLanguageService;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages an Organization's automation rules. A rule is written freely and only
 * checked in full when somebody enables it: that is the moment ReleaseFlow promises
 * the deliveries can be made. Disabling or archiving a rule cancels the runs it left
 * behind, so a rule that was turned off never delivers afterwards.
 */
@Service
public class AutomationRuleService {

    private static final String NAME_CONSTRAINT = "automation_rules_name_unique";

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRuleActionRepository actionRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationSecrets secrets;
    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;
    private final ProjectService projectService;
    private final SourceAccess sourceAccess;
    private final Map<ActionType, RuleActionExecutor> executors;
    private final Clock clock;

    AutomationRuleService(
            AutomationRuleRepository ruleRepository,
            AutomationRuleActionRepository actionRepository,
            AutomationRunRepository runRepository,
            AutomationActionRunRepository actionRunRepository,
            AutomationSecrets secrets,
            AudienceService audienceService,
            ReleaseLanguageService releaseLanguageService,
            ProjectService projectService,
            SourceAccess sourceAccess,
            List<RuleActionExecutor> executors,
            Clock clock
    ) {
        this.ruleRepository = ruleRepository;
        this.actionRepository = actionRepository;
        this.runRepository = runRepository;
        this.actionRunRepository = actionRunRepository;
        this.secrets = secrets;
        this.audienceService = audienceService;
        this.releaseLanguageService = releaseLanguageService;
        this.projectService = projectService;
        this.sourceAccess = sourceAccess;
        this.executors = executors.stream().collect(Collectors.toMap(
                RuleActionExecutor::actionType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Two executors claim " + first.actionType() + ".");
                },
                () -> new EnumMap<>(ActionType.class)
        ));
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AutomationRuleView> list(UUID organizationId) {
        List<AutomationRule> rules = ruleRepository.findAllByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId);
        if (rules.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AutomationRuleAction>> byRule = actionRepository
                .findAllByRuleIdInOrderByRuleIdAscPositionAsc(rules.stream().map(AutomationRule::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(AutomationRuleAction::getRuleId));
        Map<UUID, String> audiences = audienceNames(organizationId);
        Map<UUID, String> projects = projectNames(organizationId);
        return rules.stream()
                .map(rule -> view(rule, byRule.getOrDefault(rule.getId(), List.of()), audiences, projects))
                .toList();
    }

    @Transactional(readOnly = true)
    public AutomationRuleView get(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        return view(
                rule,
                actionRepository.findAllByRuleIdOrderByPositionAsc(rule.getId()),
                audienceNames(organizationId),
                projectNames(organizationId)
        );
    }

    @Transactional
    public AutomationRuleView create(UUID organizationId, AutomationRuleRequest request) {
        Instant now = now();
        requireProject(organizationId, request.getProjectId());
        AutomationRule rule = new AutomationRule(
                UUID.randomUUID(),
                organizationId,
                request.getName(),
                request.getTriggerType(),
                request.getProjectId(),
                now
        );
        save(rule);
        replaceActions(rule, request);
        return get(organizationId, rule.getId());
    }

    @Transactional
    public AutomationRuleView update(UUID organizationId, UUID ruleId, AutomationRuleRequest request) {
        AutomationRule rule = find(organizationId, ruleId);
        requireProject(organizationId, request.getProjectId());
        rule.redefine(request.getName(), request.getTriggerType(), request.getProjectId(), now());
        save(rule);
        replaceActions(rule, request);
        return get(organizationId, ruleId);
    }

    /**
     * Checks, in order, that the rule's project is the Organization's, that every
     * audience and language it writes for exist, that a GitHub Release action has one
     * repository to publish to, that this deployment can carry the action out at all,
     * and that its configuration and secret are usable. Only then does the rule fire.
     */
    @Transactional
    public AutomationRuleView enable(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        requireProject(organizationId, rule.getProjectId());
        List<AutomationRuleAction> actions = actionRepository.findAllByRuleIdOrderByPositionAsc(ruleId);
        if (actions.isEmpty()) {
            throw AutomationActionInvalidException.actionRequired();
        }
        List<String> languages = releaseLanguageService.targetLanguages(organizationId);
        for (AutomationRuleAction action : actions) {
            requireAudience(organizationId, action.getAudienceId());
            requireLanguage(languages, action.getTargetLanguage());
            if (action.getActionType() == ActionType.GITHUB_RELEASE && rule.getProjectId() != null
                    && sourceAccess.findSole(organizationId, rule.getProjectId(), SourceType.GITHUB).isEmpty()) {
                throw AutomationConflictException.gitHubSourceMissing();
            }
            RuleActionExecutor executor = executor(action.getActionType());
            executor.validateAvailability();
            executor.validate(action.getConfiguration(), decrypt(rule, action));
        }
        rule.enable(now());
        save(rule);
        return get(organizationId, ruleId);
    }

    @Transactional
    public AutomationRuleView disable(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        rule.disable(now());
        save(rule);
        cancelUnfinishedRuns(rule.getId());
        return get(organizationId, ruleId);
    }

    @Transactional
    public void archive(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        rule.archive(now());
        save(rule);
        cancelUnfinishedRuns(rule.getId());
    }

    /** The rule as the worker needs it, already proven to belong to the Organization. */
    AutomationRule require(UUID organizationId, UUID ruleId) {
        return find(organizationId, ruleId);
    }

    private void replaceActions(AutomationRule rule, AutomationRuleRequest request) {
        List<AutomationActionRequest> described = request.describedActions();
        if (described.isEmpty()) {
            throw AutomationActionInvalidException.actionRequired();
        }
        Map<UUID, AutomationRuleAction> existing = actionRepository
                .findAllByRuleIdOrderByPositionAsc(rule.getId()).stream()
                .collect(Collectors.toMap(AutomationRuleAction::getId, Function.identity()));
        List<String> languages = releaseLanguageService.targetLanguages(rule.getOrganizationId());
        Set<UUID> kept = new HashSet<>();
        List<AutomationRuleAction> actions = new ArrayList<>();
        for (int position = 0; position < described.size(); position++) {
            AutomationRuleAction action = action(rule, described.get(position), position, existing, languages);
            kept.add(action.getId());
            actions.add(action);
        }
        existing.values().stream()
                .filter(action -> !kept.contains(action.getId()))
                .forEach(actionRepository::delete);
        actionRepository.saveAllAndFlush(actions);
    }

    /**
     * One step, either rewritten in place or created. An Action keeps its identity
     * while its kind does, because its secret is bound to that identity; changing the
     * kind makes a new Action and forgets the old secret.
     */
    private AutomationRuleAction action(
            AutomationRule rule,
            AutomationActionRequest request,
            int position,
            Map<UUID, AutomationRuleAction> existing,
            List<String> languages
    ) {
        if (request.getActionType() == null || request.getAudienceId() == null) {
            throw AutomationActionInvalidException.actionRequired();
        }
        requireAudience(rule.getOrganizationId(), request.getAudienceId());
        String language = request.getLanguage() == null ? languages.getFirst() : request.getLanguage();
        requireLanguage(languages, language);
        Map<String, String> configuration = configuration(request);

        AutomationRuleAction previous = request.getId() == null ? null : existing.get(request.getId());
        if (previous != null && previous.getActionType() != request.getActionType()) {
            previous = null;
        }
        UUID actionId = previous == null ? UUID.randomUUID() : previous.getId();
        AutomationSecrets.Secret secret = previous == null ? null : previous.getSecret();
        String rawSecret = request.getSecret();
        if (rawSecret == null) {
            rawSecret = decrypt(rule, secret, actionId, request.getActionType());
        } else {
            secret = secrets.encrypt(
                    rawSecret, rule.getOrganizationId(), rule.getId(), actionId, request.getActionType());
        }
        executor(request.getActionType()).validate(configuration, rawSecret);

        if (previous != null) {
            previous.update(position, request.getAudienceId(), language, configuration, secret);
            return previous;
        }
        return new AutomationRuleAction(
                actionId,
                rule.getId(),
                rule.getOrganizationId(),
                position,
                request.getActionType(),
                request.getAudienceId(),
                language,
                configuration,
                secret
        );
    }

    private static Map<String, String> configuration(AutomationActionRequest request) {
        Map<String, String> configuration = new LinkedHashMap<>();
        if (request.getActionType() == ActionType.EMAIL && request.getRecipients() != null) {
            configuration.put(EmailRecipients.KEY, request.getRecipients());
        }
        return configuration;
    }

    private void cancelUnfinishedRuns(UUID ruleId) {
        Instant now = now();
        List<AutomationRun> runs = runRepository.findAllByRuleIdAndStatusIn(
                ruleId, List.of(ExecutionStatus.PENDING, ExecutionStatus.RUNNING));
        for (AutomationRun run : runs) {
            run.requestCancellation();
            List<AutomationActionRun> actions = actionRunRepository.findAllByRunIdOrderByPositionAsc(run.getId());
            // An Action already running records its own result first; the rest stop here.
            if (actions.stream().noneMatch(action -> action.getStatus() == ExecutionStatus.RUNNING)) {
                actions.forEach(action -> action.cancelPending(now));
                run.settle(actions, now);
            }
        }
        runRepository.flush();
        actionRunRepository.flush();
    }

    private String decrypt(AutomationRule rule, AutomationRuleAction action) {
        return decrypt(rule, action.getSecret(), action.getId(), action.getActionType());
    }

    private String decrypt(
            AutomationRule rule,
            AutomationSecrets.Secret secret,
            UUID actionId,
            ActionType actionType
    ) {
        return secrets.decrypt(secret, rule.getOrganizationId(), rule.getId(), actionId, actionType);
    }

    private RuleActionExecutor executor(ActionType actionType) {
        RuleActionExecutor executor = executors.get(actionType);
        if (executor == null) {
            throw new IllegalStateException("No executor is registered for " + actionType + ".");
        }
        return executor;
    }

    private void save(AutomationRule rule) {
        try {
            ruleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, NAME_CONSTRAINT) ? AutomationConflictException.nameTaken() : exception;
        }
    }

    private AutomationRule find(UUID organizationId, UUID ruleId) {
        return ruleRepository.findByIdAndOrganizationIdAndActiveTrue(ruleId, organizationId)
                .orElseThrow(AutomationRuleNotFoundException::new);
    }

    private void requireProject(UUID organizationId, UUID projectId) {
        if (projectId == null) {
            return;
        }
        try {
            projectService.get(organizationId, projectId);
        } catch (ProjectNotFoundException exception) {
            throw AutomationActionInvalidException.projectNotFound();
        }
    }

    private void requireAudience(UUID organizationId, UUID audienceId) {
        try {
            audienceService.get(organizationId, audienceId);
        } catch (AudienceNotFoundException exception) {
            throw AutomationActionInvalidException.audienceNotFound();
        }
    }

    private static void requireLanguage(List<String> languages, String language) {
        if (!languages.contains(language)) {
            throw AutomationActionInvalidException.languageNotConfigured(language);
        }
    }

    private Map<UUID, String> audienceNames(UUID organizationId) {
        return audienceService.list(organizationId).stream()
                .collect(Collectors.toMap(AudienceView::id, AudienceView::displayName));
    }

    private Map<UUID, String> projectNames(UUID organizationId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        projectService.list(organizationId).forEach(project -> names.put(project.id(), project.name()));
        return names;
    }

    private static AutomationRuleView view(
            AutomationRule rule,
            List<AutomationRuleAction> actions,
            Map<UUID, String> audiences,
            Map<UUID, String> projects
    ) {
        return new AutomationRuleView(
                rule.getId(),
                rule.getName(),
                rule.getTriggerType(),
                rule.getProjectId(),
                rule.getProjectId() == null ? null : projects.get(rule.getProjectId()),
                rule.isEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                actions.stream()
                        .map(action -> new AutomationActionView(
                                action.getId(),
                                action.getPosition(),
                                action.getActionType(),
                                action.getAudienceId(),
                                Optional.ofNullable(audiences.get(action.getAudienceId())).orElse("Unknown audience"),
                                action.getTargetLanguage(),
                                action.getConfiguration().get(EmailRecipients.KEY),
                                action.hasSecret()
                        ))
                        .toList()
        );
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
