package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.release.ReleasePublished;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Records that a release was published, and nothing else. It runs inside the
 * transaction that publishes the release, so it writes one outbox row and never
 * matches a Rule, reads a note, or calls anything: a rule nobody can carry out must
 * not be able to undo a publication. The worker turns the row into Runs after the
 * commit. A release is published once, so the row is written once.
 */
@Component
class ReleasePublishedListener {

    private final AutomationPublishJobRepository jobRepository;
    private final Clock clock;

    ReleasePublishedListener(AutomationPublishJobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @EventListener
    void onReleasePublished(ReleasePublished event) {
        if (jobRepository.existsByReleaseId(event.releaseId())) {
            return;
        }
        jobRepository.save(new AutomationPublishJob(
                UUID.randomUUID(),
                event.organizationId(),
                event.projectId(),
                event.releaseId(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }
}
