package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.change.ChangeCategory;
import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeReviewService;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.InvalidChangeReviewException;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Releases of a Project's changes, from draft through review and approval to publication.
 * Every operation is scoped by Organization and Project; a change belongs to at most one
 * release, and a published release never changes again.
 */
@Service
class ReleaseService {

    private static final String VERSION_CONSTRAINT = "releases_project_version_unique";
    private static final String CHANGE_UNIQUE_CONSTRAINT = "release_changes_change_unique";
    private static final TypeReference<List<ReleaseNoteSection>> SECTIONS = new TypeReference<>() {
    };

    private final ProjectService projectService;
    private final ChangeInboxService changeService;
    private final ChangeReviewService changeReviewService;
    private final ReleaseRepository releaseRepository;
    private final ReleaseChangeRepository releaseChangeRepository;
    private final ReleaseChangeReviewRepository reviewRepository;
    private final ReleaseNoteRepository releaseNoteRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ReleaseService(
            ProjectService projectService,
            ChangeInboxService changeService,
            ChangeReviewService changeReviewService,
            ReleaseRepository releaseRepository,
            ReleaseChangeRepository releaseChangeRepository,
            ReleaseChangeReviewRepository reviewRepository,
            ReleaseNoteRepository releaseNoteRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.projectService = projectService;
        this.changeService = changeService;
        this.changeReviewService = changeReviewService;
        this.releaseRepository = releaseRepository;
        this.releaseChangeRepository = releaseChangeRepository;
        this.reviewRepository = reviewRepository;
        this.releaseNoteRepository = releaseNoteRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<ReleaseSummary> list(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        return releaseRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .map(release -> new ReleaseSummary(
                        release.getId(),
                        release.getVersion(),
                        release.getSummary(),
                        release.getStatus(),
                        releaseChangeRepository.countByReleaseIdAndOrganizationId(release.getId(), organizationId),
                        reviewRepository.countByReleaseIdAndOrganizationId(release.getId(), organizationId),
                        release.getCreatedAt(),
                        release.getUpdatedAt(),
                        release.getPlannedReleaseAt(),
                        release.getApprovedAt(),
                        release.getPublishedAt()
                ))
                .toList();
    }

    @Transactional
    ReleaseView createDraft(UUID organizationId, UUID projectId, NewReleaseRequest request) {
        projectService.get(organizationId, projectId);
        Instant plannedReleaseAt = request.plannedInstant();
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndVersionIgnoreCase(
                organizationId,
                projectId,
                request.getVersion()
        )) {
            throw new ReleaseVersionTakenException();
        }
        Instant now = clock.instant();
        Release release = new Release(
                UUID.randomUUID(),
                organizationId,
                projectId,
                request.getVersion(),
                request.getSummary(),
                now
        );
        release.schedule(plannedReleaseAt, now);
        try {
            releaseRepository.saveAndFlush(release);
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, List.of());
    }

    @Transactional(readOnly = true)
    ReleaseView get(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView edit(UUID organizationId, UUID projectId, UUID releaseId, ReleaseRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndVersionIgnoreCaseAndIdNot(
                organizationId,
                projectId,
                request.getVersion(),
                releaseId
        )) {
            throw new ReleaseVersionTakenException();
        }
        release.edit(request.getVersion(), request.getSummary(), clock.instant());
        try {
            releaseRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView schedule(UUID organizationId, UUID projectId, UUID releaseId, ReleaseScheduleRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireUnpublished();
        release.schedule(request.plannedInstant(), clock.instant());
        return view(release, includedChanges(release));
    }

    // The database cascade removes the release's decisions and returns its changes to the
    // available pool.
    @Transactional
    void discard(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireUnpublished();
        releaseRepository.delete(release);
    }

    @Transactional(readOnly = true)
    List<ChangeView> availableChanges(UUID organizationId, UUID projectId, UUID releaseId) {
        find(organizationId, projectId, releaseId);
        Set<UUID> inAnyRelease = membership(organizationId, projectId).keySet();
        return changeService.releasableChanges(organizationId, projectId).stream()
                .filter(change -> !inAnyRelease.contains(change.id()))
                .toList();
    }

    @Transactional
    ReleaseView addChanges(UUID organizationId, UUID projectId, UUID releaseId, ReleaseChangesRequest request) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        Map<UUID, UUID> membership = membership(organizationId, projectId);

        List<ChangeView> candidates;
        if (request.isAllAvailable()) {
            candidates = changeService.releasableChanges(organizationId, projectId);
        } else {
            Set<UUID> ids = new LinkedHashSet<>(request.getChangeIds());
            candidates = changeService.changes(organizationId, projectId, ids);
            if (candidates.size() != ids.size()) {
                throw new ChangeNotFoundException();
            }
            for (ChangeView change : candidates) {
                UUID owner = membership.get(change.id());
                if (change.processing() || (owner != null && !owner.equals(release.getId()))) {
                    throw new ChangeNotReleasableException();
                }
            }
        }

        Instant now = clock.instant();
        List<ReleaseChange> additions = candidates.stream()
                .filter(change -> !membership.containsKey(change.id()))
                .map(change -> new ReleaseChange(release.getId(), change.id(), organizationId, projectId, now))
                .toList();
        try {
            releaseChangeRepository.saveAllAndFlush(additions);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, CHANGE_UNIQUE_CONSTRAINT) ? new ChangeNotReleasableException() : exception;
        }
        if (!additions.isEmpty()) {
            release.touch(now);
        }
        return view(release, includedChanges(release));
    }

    /**
     * Removes a change from a draft, or rejects it during review. Its decision goes with it,
     * and the change becomes available for another release.
     */
    @Transactional
    ReleaseView removeChange(UUID organizationId, UUID projectId, UUID releaseId, UUID changeId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireChangesRemovable();
        if (releaseChangeRepository.deleteByReleaseIdAndChangeIdAndOrganizationId(releaseId, changeId, organizationId) == 0) {
            throw new ChangeNotFoundException();
        }
        release.touch(clock.instant());
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView requestReview(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.requireDraft();
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            throw new ReleaseEmptyException();
        }
        release.requestReview(clock.instant());
        return view(release, changes);
    }

    /**
     * Records a decision on one change of a release in review. Approving confirms the
     * classification the reviewer saw; editing corrects it. Either way the change itself is
     * reviewed by the same person, exactly as in the Change Inbox.
     */
    @Transactional
    ReleaseView decide(
            ReleaseFlowPrincipal reviewer,
            UUID projectId,
            UUID releaseId,
            UUID changeId,
            ReleaseDecisionRequest request
    ) {
        UUID organizationId = reviewer.organizationId();
        Release release = find(organizationId, projectId, releaseId);
        release.requireInReview();
        ChangeView change = includedChanges(release).stream()
                .filter(included -> included.id().equals(changeId))
                .findFirst()
                .orElseThrow(ChangeNotFoundException::new);
        if (request.getAction() == ReviewAction.APPROVE) {
            if (change.category() == ChangeCategory.UNKNOWN) {
                throw new InvalidChangeReviewException();
            }
            if (!change.category().getValue().equals(request.getCategory())
                    || change.breaking() != request.getBreaking()) {
                throw new ClassificationChangedException();
            }
        }
        changeReviewService.review(reviewer, projectId, changeId, request.changeReview());

        Instant now = clock.instant();
        ReleaseChangeReview decision = reviewRepository.findById(new ReleaseChange.Key(releaseId, changeId))
                .orElseGet(() -> new ReleaseChangeReview(releaseId, changeId, organizationId, projectId));
        decision.record(request.getAction(), reviewer.userId(), reviewer.displayName(), request.getNote(), now);
        reviewRepository.saveAndFlush(decision);
        release.touch(now);
        return view(release, includedChanges(release));
    }

    @Transactional
    ReleaseView approve(ReleaseFlowPrincipal approver, UUID projectId, UUID releaseId) {
        Release release = find(approver.organizationId(), projectId, releaseId);
        release.requireInReview();
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            throw new ReleaseEmptyException();
        }
        long decided = reviewRepository.countByReleaseIdAndOrganizationId(releaseId, approver.organizationId());
        if (decided != changes.size() || changes.stream().anyMatch(ChangeView::needsReview)) {
            throw new ReleaseReviewIncompleteException();
        }
        release.approve(approver.userId(), approver.displayName(), clock.instant());
        releaseRepository.flush();
        return view(release, changes);
    }

    /**
     * Reopens a release in review or approved. Its decisions and approval are dropped; the
     * reviews recorded on its changes stay.
     */
    @Transactional
    ReleaseView returnToDraft(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        release.returnToDraft(clock.instant());
        // The database keeps an approved release's decisions fixed, so the release must be
        // a draft again before they are removed.
        releaseRepository.flush();
        reviewRepository.deleteByReleaseIdAndOrganizationId(releaseId, organizationId);
        reviewRepository.flush();
        return view(release, includedChanges(release));
    }

    /**
     * Freezes an approved release: its release note is rendered once from the current
     * changes and stored as an immutable snapshot, together with who published it and when.
     */
    @Transactional
    ReleaseView publish(ReleaseFlowPrincipal publisher, UUID projectId, UUID releaseId) {
        Release release = find(publisher.organizationId(), projectId, releaseId);
        Instant now = clock.instant();
        release.publish(publisher.userId(), publisher.displayName(), now);
        List<ChangeView> changes = includedChanges(release);
        List<ReleaseNoteSection> sections = ReleaseNotePreview.sections(changes);
        releaseNoteRepository.save(new ReleaseNote(
                release.getId(),
                release.getOrganizationId(),
                release.getProjectId(),
                release.getVersion(),
                release.getSummary(),
                objectMapper.writeValueAsString(sections),
                ReleaseNoteMarkdown.render(release.getVersion(), release.getSummary(), sections),
                now
        ));
        try {
            releaseRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, changes);
    }

    @Transactional(readOnly = true)
    List<ReleaseAssignment> assignments(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        Map<UUID, Release> releases = releaseRepository
                .findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .collect(Collectors.toMap(Release::getId, Function.identity()));
        return releaseChangeRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .map(item -> {
                    Release release = releases.get(item.getReleaseId());
                    return new ReleaseAssignment(item.getChangeId(), release.getId(), release.getVersion(), release.getStatus());
                })
                .toList();
    }

    private Release find(UUID organizationId, UUID projectId, UUID releaseId) {
        return releaseRepository.findByIdAndOrganizationIdAndProjectId(releaseId, organizationId, projectId)
                .orElseThrow(ReleaseNotFoundException::new);
    }

    private List<ChangeView> includedChanges(Release release) {
        List<UUID> ids = releaseChangeRepository
                .findAllByReleaseIdAndOrganizationId(release.getId(), release.getOrganizationId())
                .stream()
                .map(ReleaseChange::getChangeId)
                .toList();
        return changeService.changes(release.getOrganizationId(), release.getProjectId(), ids);
    }

    // Change ID -> release ID for every change of the Project that is already in a release.
    private Map<UUID, UUID> membership(UUID organizationId, UUID projectId) {
        return releaseChangeRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream()
                .collect(Collectors.toMap(ReleaseChange::getChangeId, ReleaseChange::getReleaseId));
    }

    private ReleaseView view(Release release, List<ChangeView> changes) {
        List<ReleaseDecisionView> decisions = reviewRepository
                .findAllByReleaseIdAndOrganizationId(release.getId(), release.getOrganizationId())
                .stream()
                .map(ReleaseChangeReview::view)
                .toList();
        if (!release.isPublished()) {
            return view(release, changes, decisions, ReleaseNotePreview.sections(changes), null);
        }
        ReleaseNote note = releaseNoteRepository
                .findByReleaseIdAndOrganizationIdAndProjectId(
                        release.getId(),
                        release.getOrganizationId(),
                        release.getProjectId()
                )
                .orElseThrow(() -> new IllegalStateException("Published release " + release.getId() + " has no note."));
        return view(release, changes, decisions, objectMapper.readValue(note.getSections(), SECTIONS), note.getMarkdown());
    }

    private static ReleaseView view(
            Release release,
            List<ChangeView> changes,
            List<ReleaseDecisionView> decisions,
            List<ReleaseNoteSection> sections,
            String markdown
    ) {
        return new ReleaseView(
                release.getId(),
                release.getProjectId(),
                release.getVersion(),
                release.getSummary(),
                release.getStatus(),
                release.getCreatedAt(),
                release.getUpdatedAt(),
                release.getPlannedReleaseAt(),
                changes,
                decisions,
                decisions.size(),
                sections,
                release.getApprovedAt(),
                release.getApproverName(),
                release.getPublishedAt(),
                release.getPublisherName(),
                markdown
        );
    }

    private static RuntimeException translate(DataIntegrityViolationException exception) {
        if (violates(exception, VERSION_CONSTRAINT)) {
            return new ReleaseVersionTakenException();
        }
        return exception;
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
