package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.change.ChangeNotFoundException;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
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
import java.util.stream.Collectors;

/**
 * Draft Releases of settled changes and their publication. Every operation is scoped
 * by Organization and Project; a Project has at most one draft, a change belongs to at
 * most one release, and a published release never changes again.
 */
@Service
class ReleaseService {

    private static final String ONE_DRAFT_CONSTRAINT = "releases_one_draft_per_project";
    private static final String VERSION_CONSTRAINT = "releases_project_version_unique";
    private static final String CHANGE_UNIQUE_CONSTRAINT = "release_changes_change_unique";
    private static final TypeReference<List<ReleaseNoteSection>> SECTIONS = new TypeReference<>() {
    };

    private final ProjectService projectService;
    private final ChangeInboxService changeService;
    private final ReleaseRepository releaseRepository;
    private final ReleaseChangeRepository releaseChangeRepository;
    private final ReleaseNoteRepository releaseNoteRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ReleaseService(
            ProjectService projectService,
            ChangeInboxService changeService,
            ReleaseRepository releaseRepository,
            ReleaseChangeRepository releaseChangeRepository,
            ReleaseNoteRepository releaseNoteRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.projectService = projectService;
        this.changeService = changeService;
        this.releaseRepository = releaseRepository;
        this.releaseChangeRepository = releaseChangeRepository;
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
                        release.getCreatedAt(),
                        release.getUpdatedAt(),
                        release.getPublishedAt()
                ))
                .toList();
    }

    @Transactional
    ReleaseView createDraft(UUID organizationId, UUID projectId, ReleaseRequest request) {
        projectService.get(organizationId, projectId);
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndStatus(organizationId, projectId, ReleaseStatus.DRAFT)) {
            throw new DraftReleaseExistsException();
        }
        if (releaseRepository.existsByOrganizationIdAndProjectIdAndVersionIgnoreCase(
                organizationId,
                projectId,
                request.getVersion()
        )) {
            throw new ReleaseVersionTakenException();
        }
        Release release = new Release(
                UUID.randomUUID(),
                organizationId,
                projectId,
                request.getVersion(),
                request.getSummary(),
                clock.instant()
        );
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
        Release release = findDraft(organizationId, projectId, releaseId);
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

    // The database cascade returns the draft's changes to the available pool.
    @Transactional
    void discard(UUID organizationId, UUID projectId, UUID releaseId) {
        releaseRepository.delete(findDraft(organizationId, projectId, releaseId));
    }

    @Transactional(readOnly = true)
    List<ChangeView> availableChanges(UUID organizationId, UUID projectId, UUID releaseId) {
        find(organizationId, projectId, releaseId);
        Set<UUID> inAnyRelease = membership(organizationId, projectId).keySet();
        return changeService.settledChanges(organizationId, projectId).stream()
                .filter(change -> !inAnyRelease.contains(change.id()))
                .toList();
    }

    @Transactional
    ReleaseView addChanges(UUID organizationId, UUID projectId, UUID releaseId, ReleaseChangesRequest request) {
        Release release = findDraft(organizationId, projectId, releaseId);
        Map<UUID, UUID> membership = membership(organizationId, projectId);

        List<ChangeView> candidates;
        if (request.isAllAvailable()) {
            candidates = changeService.settledChanges(organizationId, projectId);
        } else {
            Set<UUID> ids = new LinkedHashSet<>(request.getChangeIds());
            candidates = changeService.changes(organizationId, projectId, ids);
            if (candidates.size() != ids.size()) {
                throw new ChangeNotFoundException();
            }
            for (ChangeView change : candidates) {
                UUID owner = membership.get(change.id());
                if (change.needsReview() || (owner != null && !owner.equals(release.getId()))) {
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

    @Transactional
    ReleaseView removeChange(UUID organizationId, UUID projectId, UUID releaseId, UUID changeId) {
        Release release = findDraft(organizationId, projectId, releaseId);
        if (releaseChangeRepository.deleteByReleaseIdAndChangeIdAndOrganizationId(releaseId, changeId, organizationId) == 0) {
            throw new ChangeNotFoundException();
        }
        release.touch(clock.instant());
        return view(release, includedChanges(release));
    }

    /**
     * Freezes the draft: its release note is rendered once from the current changes and
     * stored as an immutable snapshot, together with who published it and when.
     */
    @Transactional
    ReleaseView publish(ReleaseFlowPrincipal publisher, UUID projectId, UUID releaseId) {
        Release release = findDraft(publisher.organizationId(), projectId, releaseId);
        List<ChangeView> changes = includedChanges(release);
        if (changes.isEmpty()) {
            throw new ReleaseEmptyException();
        }
        Instant now = clock.instant();
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
        release.publish(publisher.userId(), publisher.displayName(), now);
        try {
            releaseRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
        return view(release, changes);
    }

    private Release find(UUID organizationId, UUID projectId, UUID releaseId) {
        return releaseRepository.findByIdAndOrganizationIdAndProjectId(releaseId, organizationId, projectId)
                .orElseThrow(ReleaseNotFoundException::new);
    }

    private Release findDraft(UUID organizationId, UUID projectId, UUID releaseId) {
        Release release = find(organizationId, projectId, releaseId);
        if (!release.isDraft()) {
            throw new ReleasePublishedException();
        }
        return release;
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
        if (release.isDraft()) {
            return view(release, changes, ReleaseNotePreview.sections(changes), null);
        }
        ReleaseNote note = releaseNoteRepository
                .findByReleaseIdAndOrganizationIdAndProjectId(
                        release.getId(),
                        release.getOrganizationId(),
                        release.getProjectId()
                )
                .orElseThrow(() -> new IllegalStateException("Published release " + release.getId() + " has no note."));
        return view(release, changes, objectMapper.readValue(note.getSections(), SECTIONS), note.getMarkdown());
    }

    private static ReleaseView view(
            Release release,
            List<ChangeView> changes,
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
                changes,
                sections,
                release.getPublishedAt(),
                release.getPublisherName(),
                markdown
        );
    }

    private static RuntimeException translate(DataIntegrityViolationException exception) {
        if (violates(exception, ONE_DRAFT_CONSTRAINT)) {
            return new DraftReleaseExistsException();
        }
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
