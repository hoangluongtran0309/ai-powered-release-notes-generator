package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.audience.AudienceNotFoundException;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.AudienceView;
import com.hoangluongtran0309.releaseflow.audience.ReleaseLanguageService;
import com.hoangluongtran0309.releaseflow.project.ProjectNotFoundException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseAccess;
import com.hoangluongtran0309.releaseflow.release.ReleaseStatus;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final String WEBHOOK_PATH_PREFIX = "/webhooks/automation/";
    private static final int WEBHOOK_SECRET_BYTES = 32;
    private static final int REMINDER_MAX_DAYS = 365;

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRuleActionRepository actionRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationSecrets secrets;
    private final AudienceService audienceService;
    private final ReleaseLanguageService releaseLanguageService;
    private final ProjectService projectService;
    private final SourceAccess sourceAccess;
    private final ReleaseAccess releaseAccess;
    private final Map<ActionType, RuleActionExecutor> executors;
    private final SecureRandom secureRandom;
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
            ReleaseAccess releaseAccess,
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
        this.releaseAccess = releaseAccess;
        this.secureRandom = new SecureRandom();
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

    /**
     * Writes a rule. A rule called by another system is given its path and its secret
     * here, and the secret is in the answer to this call and nowhere else afterwards.
     */
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
        String rawWebhookSecret = configureTrigger(rule, request, null, null);
        save(rule);
        replaceActions(rule, request);
        return reveal(get(organizationId, rule.getId()), rawWebhookSecret);
    }

    @Transactional
    public AutomationRuleView update(UUID organizationId, UUID ruleId, AutomationRuleRequest request) {
        AutomationRule rule = find(organizationId, ruleId);
        requireProject(organizationId, request.getProjectId());
        // A webhook rule that stays one keeps its path and its secret, so the systems
        // already calling it are not silently cut off by an edit.
        boolean keepsWebhook = rule.getTriggerType() == TriggerType.EXTERNAL_WEBHOOK
                && request.getTriggerType() == TriggerType.EXTERNAL_WEBHOOK;
        UUID keptWebhookId = keepsWebhook ? rule.getWebhookId() : null;
        AutomationSecrets.Secret keptSecret = keepsWebhook ? rule.getWebhookSecret() : null;
        rule.redefine(request.getName(), request.getTriggerType(), request.getProjectId(), now());
        String rawWebhookSecret = configureTrigger(rule, request, keptWebhookId, keptSecret);
        save(rule);
        replaceActions(rule, request);
        return reveal(get(organizationId, ruleId), rawWebhookSecret);
    }

    /** The next firing of a schedule, so a person can see what they just wrote. */
    public Instant previewCron(String expression, String timeZone) {
        return CronSchedule.of(expression, timeZone).nextAfter(now());
    }

    /**
     * A new secret for the same webhook path. The old one stops proving anything the
     * moment this returns, and the new one is in this answer only.
     */
    @Transactional
    public AutomationRuleView rotateWebhookSecret(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        if (rule.getTriggerType() != TriggerType.EXTERNAL_WEBHOOK || rule.getWebhookId() == null) {
            throw AutomationConflictException.webhookRuleRequired();
        }
        String rawSecret = generateWebhookSecret();
        rule.rotateWebhookSecret(
                secrets.encryptWebhookSecret(rawSecret, organizationId, rule.getId(), rule.getWebhookId()),
                now()
        );
        save(rule);
        return reveal(get(organizationId, ruleId), rawSecret);
    }

    /**
     * Remembers what this trigger, and only this trigger, needs: the published release
     * a schedule repeats and the schedule itself, how many days before a planned release
     * a reminder goes out, or the path and secret another system calls with. Changing
     * the trigger forgets the rest.
     *
     * @return the raw webhook secret when one was just minted, otherwise null
     */
    private String configureTrigger(
            AutomationRule rule,
            AutomationRuleRequest request,
            UUID keptWebhookId,
            AutomationSecrets.Secret keptSecret
    ) {
        Instant now = now();
        switch (request.getTriggerType()) {
            case SCHEDULED_CRON -> {
                ReleaseAccess.ReleaseSnapshot release = releaseAccess
                        .find(rule.getOrganizationId(), request.getReleaseId())
                        .orElseThrow(AutomationActionInvalidException::cronReleaseRequired);
                if (release.status() != ReleaseStatus.PUBLISHED) {
                    throw AutomationConflictException.releaseNotPublished();
                }
                CronSchedule schedule = CronSchedule.of(request.getCronExpression(), request.getCronTimeZone());
                // Proves the schedule comes round at all before it is ever stored.
                Instant next = schedule.nextAfter(now);
                // The project of a scheduled rule is the release's own; it is not a
                // separate choice, because the rule works on that one release.
                rule.configureCron(
                        release.releaseId(),
                        release.projectId(),
                        schedule.expressionText(),
                        schedule.zoneText(),
                        now
                );
                if (rule.isEnabled()) {
                    rule.advanceNextFireAt(next, now);
                }
            }
            case UPCOMING_RELEASE_REMINDER -> {
                Integer daysBefore = request.getDaysBefore();
                if (daysBefore == null || daysBefore < 0 || daysBefore > REMINDER_MAX_DAYS) {
                    throw AutomationActionInvalidException.reminderDaysInvalid();
                }
                rule.configureReminder(daysBefore, now);
            }
            case EXTERNAL_WEBHOOK -> {
                if (keptWebhookId != null && keptSecret != null) {
                    rule.configureWebhook(keptWebhookId, keptSecret, now);
                    return null;
                }
                UUID webhookId = UUID.randomUUID();
                String rawSecret = generateWebhookSecret();
                rule.configureWebhook(
                        webhookId,
                        secrets.encryptWebhookSecret(
                                rawSecret, rule.getOrganizationId(), rule.getId(), webhookId),
                        now
                );
                return rawSecret;
            }
            default -> rule.configureWithoutParameters(now);
        }
        return null;
    }

    /**
     * Checks, in order, that the rule's project is the Organization's, that its trigger
     * can promise what it needs, that every audience and language it writes for exist,
     * that a GitHub Release action has one repository to publish to, that this
     * deployment can carry the action out at all, and that its configuration and secret
     * are usable. Only then does the rule fire, and a schedule is booked its first
     * firing.
     */
    @Transactional
    public AutomationRuleView enable(UUID organizationId, UUID ruleId) {
        AutomationRule rule = find(organizationId, ruleId);
        requireProject(organizationId, rule.getProjectId());
        List<AutomationRuleAction> actions = actionRepository.findAllByRuleIdOrderByPositionAsc(ruleId);
        if (actions.isEmpty()) {
            throw AutomationActionInvalidException.actionRequired();
        }
        CronSchedule schedule = validateTrigger(rule, actions);
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
        Instant now = now();
        rule.enable(now);
        if (schedule != null) {
            rule.advanceNextFireAt(schedule.nextAfter(now), now);
        }
        save(rule);
        return get(organizationId, ruleId);
    }

    /**
     * What a trigger must be able to promise before its rule fires. A rule ReleaseFlow
     * sets off itself may only tell people something: nobody is watching a schedule go
     * off, so it never publishes a GitHub Release.
     *
     * @return the schedule to book the first firing from, for a cron rule
     */
    private CronSchedule validateTrigger(AutomationRule rule, List<AutomationRuleAction> actions) {
        if (rule.getTriggerType().isScheduled() && actions.stream().anyMatch(action ->
                action.getActionType() != ActionType.SLACK && action.getActionType() != ActionType.EMAIL)) {
            throw AutomationConflictException.scheduledActionUnsupported();
        }
        switch (rule.getTriggerType()) {
            case SCHEDULED_CRON -> {
                ReleaseAccess.ReleaseSnapshot release = releaseAccess
                        .find(rule.getOrganizationId(), rule.getTriggerReleaseId())
                        .orElseThrow(AutomationActionInvalidException::cronReleaseRequired);
                if (release.status() != ReleaseStatus.PUBLISHED) {
                    throw AutomationConflictException.releaseNotPublished();
                }
                return CronSchedule.of(rule.getCronExpression(), rule.getCronTimeZone());
            }
            case UPCOMING_RELEASE_REMINDER -> {
                if (rule.getReminderDaysBefore() == null) {
                    throw AutomationActionInvalidException.reminderDaysInvalid();
                }
            }
            case EXTERNAL_WEBHOOK -> {
                if (rule.getWebhookId() == null || rule.getWebhookSecret() == null) {
                    throw AutomationConflictException.webhookRuleRequired();
                }
            }
            default -> {
                // A published release or a person decides when these fire.
            }
        }
        return null;
    }

    private String generateWebhookSecret() {
        byte[] secret = new byte[WEBHOOK_SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static AutomationRuleView reveal(AutomationRuleView view, String rawSecret) {
        return rawSecret == null ? view : view.revealing(rawSecret);
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
        if (request.getActionType() == null) {
            return configuration;
        }
        switch (request.getActionType()) {
            case EMAIL -> put(configuration, EmailRecipients.KEY, request.getRecipients());
            case NOTION -> put(configuration, NotionParentPage.KEY, request.getParentPageId());
            case CONFLUENCE -> {
                put(configuration, ConfluenceSite.KEY, request.getSiteUrl());
                put(configuration, ConfluenceActionExecutor.EMAIL_KEY, request.getEmail());
                put(configuration, ConfluenceActionExecutor.SPACE_KEY, request.getSpaceId());
                put(configuration, ConfluenceActionExecutor.PARENT_KEY, request.getParentPageId());
            }
            // The repository a GitHub Release is cut in is the project's, not the rule's,
            // so it is read when the run is made rather than written down here.
            case GITHUB_RELEASE, SLACK, PUBLIC_CHANGELOG -> {
            }
        }
        return configuration;
    }

    private static void put(Map<String, String> configuration, String key, String value) {
        if (value != null) {
            configuration.put(key, value);
        }
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
                rule.getTriggerReleaseId(),
                rule.getCronExpression(),
                rule.getCronTimeZone(),
                rule.getNextFireAt(),
                rule.getReminderDaysBefore(),
                rule.getWebhookId() == null ? null : WEBHOOK_PATH_PREFIX + rule.getWebhookId(),
                rule.getWebhookSecret() != null,
                null,
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
                                action.getConfiguration().get(NotionParentPage.KEY) != null
                                        ? action.getConfiguration().get(NotionParentPage.KEY)
                                        : action.getConfiguration().get(ConfluenceActionExecutor.PARENT_KEY),
                                action.getConfiguration().get(ConfluenceSite.KEY),
                                action.getConfiguration().get(ConfluenceActionExecutor.EMAIL_KEY),
                                action.getConfiguration().get(ConfluenceActionExecutor.SPACE_KEY),
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
