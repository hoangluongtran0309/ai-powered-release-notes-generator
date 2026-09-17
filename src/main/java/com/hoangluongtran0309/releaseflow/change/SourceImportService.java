package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceService;
import com.hoangluongtran0309.releaseflow.project.IntegrationSourceView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports of a source's history: the merged pull requests of the last
 * {@value #WINDOW_DAYS} days, at most a configured number of new changes per run. The
 * worker does the reading; this service starts, resumes, and reports imports.
 */
@Service
class SourceImportService {

    static final int WINDOW_DAYS = 90;
    static final String ONE_ACTIVE_CONSTRAINT = "source_sync_jobs_one_active";

    private final IntegrationSourceService sourceService;
    private final SourceSyncJobRepository jobRepository;
    private final Clock clock;
    private final int itemLimit;

    SourceImportService(
            IntegrationSourceService sourceService,
            SourceSyncJobRepository jobRepository,
            Clock clock,
            @Value("${releaseflow.import.item-limit}") int itemLimit
    ) {
        if (itemLimit < 1) {
            throw new IllegalStateException("releaseflow.import.item-limit must be at least 1.");
        }
        this.sourceService = sourceService;
        this.jobRepository = jobRepository;
        this.clock = clock;
        this.itemLimit = itemLimit;
    }

    /** Queues an import of the last {@value #WINDOW_DAYS} days of merged pull requests. */
    @Transactional
    SourceSyncView startImport(ReleaseFlowPrincipal administrator, UUID projectId, UUID sourceId) {
        UUID organizationId = administrator.organizationId();
        IntegrationSourceView source = sourceService.get(organizationId, projectId, sourceId);
        requireImportable(source);
        if (!source.accessTokenConfigured()) {
            throw new SourceTokenMissingException();
        }
        jobRepository.findFirstBySourceIdAndOrganizationIdOrderByCreatedAtDescIdDesc(sourceId, organizationId)
                .filter(SourceImportService::active)
                .ifPresent(job -> {
                    throw new SourceSyncInProgressException();
                });
        Instant now = now();
        SourceSyncJob job = SourceSyncJob.historicalImport(
                organizationId,
                projectId,
                sourceId,
                now.minus(Duration.ofDays(WINDOW_DAYS)),
                now,
                itemLimit,
                administrator.userId(),
                administrator.displayName(),
                now
        );
        try {
            jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException exception) {
            // Another request queued an import of this source first.
            if (violates(exception, ONE_ACTIVE_CONSTRAINT)) {
                throw new SourceSyncInProgressException();
            }
            throw exception;
        }
        return view(source, job);
    }

    /** Continues the latest import, if it stopped at its limit or failed, from where it stopped. */
    @Transactional
    SourceSyncView resume(ReleaseFlowPrincipal administrator, UUID projectId, UUID sourceId) {
        UUID organizationId = administrator.organizationId();
        IntegrationSourceView source = sourceService.get(organizationId, projectId, sourceId);
        requireImportable(source);
        if (!source.accessTokenConfigured()) {
            throw new SourceTokenMissingException();
        }
        SourceSyncJob job = jobRepository.findFirstBySourceIdAndOrganizationIdOrderByCreatedAtDescIdDesc(sourceId, organizationId)
                .filter(SourceSyncJob::isResumable)
                .orElseThrow(SourceImportNotResumableException::new);
        job.resume(now());
        jobRepository.flush();
        return view(source, job);
    }

    /** Every source of the Project with its latest import. */
    @Transactional(readOnly = true)
    List<SourceSyncView> status(UUID organizationId, UUID projectId) {
        List<IntegrationSourceView> sources = sourceService.list(organizationId, projectId);
        Map<UUID, SourceSyncJob> latest = jobRepository
                .findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .collect(Collectors.toMap(SourceSyncJob::getSourceId, Function.identity(), (newer, older) -> newer));
        return sources.stream().map(source -> view(source, latest.get(source.id()))).toList();
    }

    // A polled source syncs itself, so asking for an import by hand is refused too.
    private static void requireImportable(IntegrationSourceView source) {
        if (!source.type().supportsHistoryImport()) {
            throw new SourceImportNotSupportedException(source.type());
        }
    }

    static SourceSyncView view(IntegrationSourceView source, SourceSyncJob job) {
        return new SourceSyncView(
                source.id(),
                source.externalProjectKey(),
                source.deliveryMechanism(),
                source.type().supportsHistoryImport(),
                source.type().isPolled(),
                source.nextPollAt(),
                source.accessTokenConfigured(),
                job == null ? null : job.getStatus().name(),
                job == null ? null : job.getWindowStart(),
                job == null ? null : job.getWindowEnd(),
                job == null ? 0 : job.getScannedCount(),
                job == null ? 0 : job.getImportedCount(),
                job == null ? 0 : job.getItemLimit(),
                job == null ? null : job.getRequesterName(),
                job == null ? null : job.getCreatedAt(),
                job == null ? null : job.getCompletedAt(),
                job == null || !active(job) ? null : job.getNextAttemptAt(),
                job == null ? null : job.getLastError(),
                source.lastSyncAt(),
                source.lastErrorCode(),
                job != null && job.isResumable()
        );
    }

    private static boolean active(SourceSyncJob job) {
        return switch (job.getStatus()) {
            case PENDING, RUNNING, RETRY_SCHEDULED -> true;
            case COMPLETED, PARTIAL, FAILED -> false;
        };
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    // PostgreSQL keeps microseconds, so a time must survive a round trip unchanged.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
