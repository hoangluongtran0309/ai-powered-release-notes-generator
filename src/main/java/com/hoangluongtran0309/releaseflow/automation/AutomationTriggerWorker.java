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
import java.util.List;
import java.util.Optional;

/**
 * Sets off the Rules nobody asks for: a schedule that has come round, and a release
 * whose planned time is near enough to tell people about. Both only write Runs, which
 * the delivery worker then walks exactly as it walks a publication's.
 *
 * <p>A schedule that was missed while ReleaseFlow was down owes one catch-up Run and
 * not a queue of them: the next firing is always booked from the present.
 */
@Component
class AutomationTriggerWorker {

    private static final Logger log = LoggerFactory.getLogger(AutomationTriggerWorker.class);

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRunRepository runRepository;
    private final AutomationRunFactory runFactory;
    private final ReleaseAccess releaseAccess;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final Duration zeroDayLookahead;

    AutomationTriggerWorker(
            AutomationRuleRepository ruleRepository,
            AutomationRunRepository runRepository,
            AutomationRunFactory runFactory,
            ReleaseAccess releaseAccess,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${releaseflow.automation.trigger-worker-enabled}") boolean enabled,
            @Value("${releaseflow.automation.trigger-batch-size}") int batchSize,
            @Value("${releaseflow.automation.reminder-zero-day-lookahead}") Duration zeroDayLookahead
    ) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.runFactory = runFactory;
        this.releaseAccess = releaseAccess;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.zeroDayLookahead = zeroDayLookahead;
    }

    @Scheduled(fixedDelayString = "${releaseflow.automation.trigger-delay}")
    void processDueTriggers() {
        if (!enabled) {
            return;
        }
        try {
            for (int booked = 0; booked < batchSize && processOne(); booked++) {
                // Keep going until the batch is full or nothing is due.
            }
        } catch (RuntimeException exception) {
            log.error("Automation triggers stopped unexpectedly; they resume on the next tick.", exception);
        }
    }

    /**
     * Books at most one firing: a schedule that has come round, or one reminder.
     *
     * @return whether there was something to set off
     */
    boolean processOne() {
        if (fireNextDueCron()) {
            return true;
        }
        return scheduleNextDueReminder();
    }

    private boolean fireNextDueCron() {
        Boolean fired = transactionTemplate.execute(status -> {
            Instant now = now();
            Optional<AutomationRule> claimed = ruleRepository.lockNextDueCron(now);
            if (claimed.isEmpty()) {
                return false;
            }
            AutomationRule rule = claimed.get();
            Instant occurrence = rule.getNextFireAt();
            if (!advance(rule, now)) {
                return true;
            }
            ReleaseAccess.ReleaseSnapshot release = releaseAccess
                    .find(rule.getOrganizationId(), rule.getTriggerReleaseId())
                    .orElse(null);
            // A release that is no longer published is simply not delivered again; the
            // schedule itself keeps its place.
            if (release == null || release.status() != ReleaseStatus.PUBLISHED) {
                log.warn("Schedule {} skipped an occurrence: its release is not published.", rule.getId());
                return true;
            }
            if (!runRepository.existsByRuleIdAndTriggerTypeAndScheduledFor(
                    rule.getId(), TriggerType.SCHEDULED_CRON, occurrence)) {
                runFactory.create(rule, release, TriggerType.SCHEDULED_CRON, null, null, null, occurrence);
            }
            return true;
        });
        return Boolean.TRUE.equals(fired);
    }

    // Books the next occurrence from now, never from the one just handled, so a week of
    // downtime costs one Run. A schedule that no longer parses stops instead of failing
    // every tick; enabling the Rule again is what gives it a new one.
    private boolean advance(AutomationRule rule, Instant now) {
        try {
            rule.advanceNextFireAt(
                    CronSchedule.of(rule.getCronExpression(), rule.getCronTimeZone()).nextAfter(now), now);
            ruleRepository.flush();
            return true;
        } catch (AutomationActionInvalidException unusable) {
            log.error("Schedule {} can no longer be read and will not fire again.", rule.getId(), unusable);
            rule.advanceNextFireAt(null, now);
            ruleRepository.flush();
            return false;
        }
    }

    /**
     * One reminder for one approved release. The moment a reminder answers is the
     * release's planned time less the rule's notice, so moving the release earns exactly
     * one new reminder and moving it twice earns two.
     */
    private boolean scheduleNextDueReminder() {
        Boolean booked = transactionTemplate.execute(status -> {
            Instant now = now();
            for (AutomationRule rule : ruleRepository
                    .findAllByTriggerTypeAndEnabledTrueAndActiveTrueOrderByCreatedAtAscIdAsc(
                            TriggerType.UPCOMING_RELEASE_REMINDER)) {
                Integer daysBefore = rule.getReminderDaysBefore();
                if (daysBefore == null) {
                    continue;
                }
                Duration notice = Duration.ofDays(daysBefore);
                // With no notice at all the reminder goes out as the release comes due,
                // so the scan looks a tick ahead rather than exactly at this instant.
                Instant until = daysBefore == 0 ? now.plus(zeroDayLookahead) : now.plus(notice);
                List<ReleaseAccess.UpcomingRelease> upcoming = releaseAccess.upcomingApproved(
                        rule.getOrganizationId(), rule.getProjectId(), now, until);
                for (ReleaseAccess.UpcomingRelease release : upcoming) {
                    Instant scheduledFor = release.plannedReleaseAt().minus(notice);
                    if (daysBefore != 0 && scheduledFor.isAfter(now)) {
                        continue;
                    }
                    if (runRepository.existsByRuleIdAndReleaseIdAndTriggerTypeAndScheduledFor(
                            rule.getId(), release.id(), TriggerType.UPCOMING_RELEASE_REMINDER, scheduledFor)) {
                        continue;
                    }
                    ReleaseAccess.ReleaseSnapshot snapshot = releaseAccess
                            .find(rule.getOrganizationId(), release.id())
                            .orElse(null);
                    if (snapshot == null || snapshot.status() != ReleaseStatus.APPROVED) {
                        continue;
                    }
                    runFactory.create(
                            rule,
                            snapshot,
                            TriggerType.UPCOMING_RELEASE_REMINDER,
                            null,
                            null,
                            null,
                            scheduledFor
                    );
                    return true;
                }
            }
            return false;
        });
        return Boolean.TRUE.equals(booked);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
