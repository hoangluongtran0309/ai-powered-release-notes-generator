package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class ChangeInboxService {

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
        return changeRepository.findInbox(organizationId, projectId, filter.category(), filter.needsReview())
                .stream()
                .map(ChangeView::from)
                .toList();
    }
}
