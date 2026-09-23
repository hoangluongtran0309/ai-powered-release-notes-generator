package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Possible duplicates of a Project and people's decisions on them. Any member may
 * decide, once. A decision never merges, removes, or reviews a change.
 */
@Service
class DuplicateCandidateService {

    private final ProjectService projectService;
    private final DuplicateCandidateRepository candidateRepository;
    private final ChangeRepository changeRepository;
    private final Clock clock;

    DuplicateCandidateService(
            ProjectService projectService,
            DuplicateCandidateRepository candidateRepository,
            ChangeRepository changeRepository,
            Clock clock
    ) {
        this.projectService = projectService;
        this.candidateRepository = candidateRepository;
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    /** The Project's possible duplicates, newest first; a null status lists every one. */
    @Transactional(readOnly = true)
    List<DuplicateCandidateView> list(UUID organizationId, UUID projectId, DuplicateCandidateStatus status) {
        projectService.get(organizationId, projectId);
        List<DuplicateCandidate> candidates = candidateRepository
                .findAllByOrganizationIdAndProjectIdOrderByCreatedAtDescIdDesc(organizationId, projectId)
                .stream()
                .filter(candidate -> status == null || candidate.getStatus() == status)
                .toList();
        Set<UUID> ids = new HashSet<>();
        candidates.forEach(candidate -> {
            ids.add(candidate.getChangeId());
            ids.add(candidate.getDuplicateOfId());
        });
        Map<UUID, Change> changes = changeRepository
                .findAllByOrganizationIdAndProjectIdAndIdInOrderByMergedAtAscIdAsc(organizationId, projectId, ids)
                .stream()
                .collect(Collectors.toMap(Change::getId, Function.identity()));
        return candidates.stream().map(candidate -> view(candidate, changes)).toList();
    }

    @Transactional
    DuplicateCandidateView decide(
            ReleaseFlowPrincipal person,
            UUID projectId,
            UUID candidateId,
            DuplicateDecisionRequest request
    ) {
        UUID organizationId = person.organizationId();
        DuplicateCandidate candidate = candidateRepository
                .findByIdAndOrganizationIdAndProjectId(candidateId, organizationId, projectId)
                .orElseThrow(DuplicateCandidateNotFoundException::new);
        candidate.decide(request.getDecision(), person.userId(), person.displayName(), clock.instant());
        candidateRepository.flush();
        Map<UUID, Change> changes = changeRepository
                .findAllByOrganizationIdAndProjectIdAndIdInOrderByMergedAtAscIdAsc(
                        organizationId, projectId, List.of(candidate.getChangeId(), candidate.getDuplicateOfId()))
                .stream()
                .collect(Collectors.toMap(Change::getId, Function.identity()));
        return view(candidate, changes);
    }

    private static DuplicateCandidateView view(DuplicateCandidate candidate, Map<UUID, Change> changes) {
        Change change = changes.get(candidate.getChangeId());
        Change earlier = changes.get(candidate.getDuplicateOfId());
        return new DuplicateCandidateView(
                candidate.getId(),
                candidate.getChangeId(),
                change.getPullRequestNumber(),
                change.getTitle(),
                whatChanged(change),
                candidate.getDuplicateOfId(),
                earlier.getPullRequestNumber(),
                earlier.getTitle(),
                whatChanged(earlier),
                candidate.getSimilarity(),
                candidate.getEvidence(),
                candidate.getStatus(),
                candidate.getDeciderName(),
                candidate.getCreatedAt(),
                candidate.getDecidedAt()
        );
    }

    private static String whatChanged(Change change) {
        return change.getNeutralSummary() == null ? null : change.getNeutralSummary().whatChanged();
    }
}
