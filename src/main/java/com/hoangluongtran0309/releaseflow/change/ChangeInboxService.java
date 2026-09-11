package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read access to a Project's changes, for the Change Inbox and for other capabilities.
 * Every query is scoped by Organization and Project.
 */
@Service
public class ChangeInboxService {

    private final ProjectService projectService;
    private final ChangeRepository changeRepository;

    ChangeInboxService(ProjectService projectService, ChangeRepository changeRepository) {
        this.projectService = projectService;
        this.changeRepository = changeRepository;
    }

    @Transactional(readOnly = true)
    List<ChangeView> list(UUID organizationId, UUID projectId, ChangeFilter filter) {
        // Rejects another tenant's Project as not found instead of returning an empty inbox.
        projectService.get(organizationId, projectId);
        return changeRepository.findInbox(
                        organizationId,
                        projectId,
                        filter.category(),
                        filter.needsReview(),
                        filter.reviewed()
                )
                .stream()
                .map(ChangeView::from)
                .toList();
    }

    /**
     * Changes that no longer need review, oldest merge first. A settled change never
     * returns to review, so it is safe to put in a release.
     */
    @Transactional(readOnly = true)
    public List<ChangeView> settledChanges(UUID organizationId, UUID projectId) {
        return changeRepository
                .findAllByOrganizationIdAndProjectIdAndNeedsReviewFalseOrderByMergedAtAscIdAsc(organizationId, projectId)
                .stream()
                .map(ChangeView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChangeView> changes(UUID organizationId, UUID projectId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return changeRepository
                .findAllByOrganizationIdAndProjectIdAndIdInOrderByMergedAtAscIdAsc(organizationId, projectId, ids)
                .stream()
                .map(ChangeView::from)
                .toList();
    }
}
