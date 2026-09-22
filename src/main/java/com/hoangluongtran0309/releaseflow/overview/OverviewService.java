package com.hoangluongtran0309.releaseflow.overview;

import com.hoangluongtran0309.releaseflow.change.ChangeCounts;
import com.hoangluongtran0309.releaseflow.change.ChangeInboxService;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import com.hoangluongtran0309.releaseflow.project.ProjectView;
import com.hoangluongtran0309.releaseflow.release.ReleaseCounts;
import com.hoangluongtran0309.releaseflow.release.ReleaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the workspace overview shows. It owns no data of its own: it asks each
 * capability for counts and turns them into the one next step a person is offered.
 */
@Service
public class OverviewService {

    private final ProjectService projectService;
    private final ChangeInboxService changeInboxService;
    private final ReleaseService releaseService;

    OverviewService(
            ProjectService projectService,
            ChangeInboxService changeInboxService,
            ReleaseService releaseService
    ) {
        this.projectService = projectService;
        this.changeInboxService = changeInboxService;
        this.releaseService = releaseService;
    }

    /**
     * @param requested the Project the person last looked at, or null to take the first
     *                  one; a Project of another Organization is simply not among them
     */
    @Transactional(readOnly = true)
    public OverviewView overview(UUID organizationId, UUID requested) {
        List<ProjectView> projects = projectService.list(organizationId);
        Optional<ProjectView> selected = select(projects, requested);
        if (selected.isEmpty()) {
            return new OverviewView(null, null, List.of(), 0, 0, 0, 0, false, NextStep.CREATE_PROJECT);
        }

        ProjectView project = selected.get();
        ChangeCounts changes = changeInboxService.counts(organizationId, project.id());
        ReleaseCounts releases = releaseService.counts(organizationId, project.id());
        boolean sourceConnected = !project.sources().isEmpty();

        return new OverviewView(
                project.id(),
                project.name(),
                projects.stream()
                        .map(option -> new OverviewView.ProjectOption(
                                option.id(),
                                option.name(),
                                option.id().equals(project.id())
                        ))
                        .toList(),
                changes.total(),
                changes.needsReview(),
                changes.breaking(),
                releases.total(),
                sourceConnected,
                NextStep.of(true, sourceConnected, changes, releases)
        );
    }

    /** The Projects a person can switch between, with the one they are looking at marked. */
    @Transactional(readOnly = true)
    public List<OverviewView.ProjectOption> projectOptions(UUID organizationId, UUID requested) {
        List<ProjectView> projects = projectService.list(organizationId);
        UUID current = select(projects, requested).map(ProjectView::id).orElse(null);
        return projects.stream()
                .map(option -> new OverviewView.ProjectOption(
                        option.id(),
                        option.name(),
                        option.id().equals(current)
                ))
                .toList();
    }

    /**
     * The Project a page should show when its own URL does not name one: the remembered
     * one if it is still this Organization's, and otherwise the first.
     *
     * <p>This is only ever a fallback. A URL that names a Project is answered by the page
     * itself, which reports a Project of another Organization as not found rather than
     * quietly showing something else.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> rememberedOrFirstProject(UUID organizationId, UUID remembered) {
        return select(projectService.list(organizationId), remembered).map(ProjectView::id);
    }

    /** Whether a Project is this Organization's, without deciding anything for the page. */
    @Transactional(readOnly = true)
    public boolean owns(UUID organizationId, UUID projectId) {
        return projectId != null && projectService.list(organizationId).stream()
                .anyMatch(project -> project.id().equals(projectId));
    }

    private static Optional<ProjectView> select(List<ProjectView> projects, UUID requested) {
        if (projects.isEmpty()) {
            return Optional.empty();
        }
        return projects.stream()
                .filter(project -> project.id().equals(requested))
                .findFirst()
                .or(() -> Optional.of(projects.getFirst()));
    }

}
