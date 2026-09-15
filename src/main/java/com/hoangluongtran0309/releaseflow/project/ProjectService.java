package com.hoangluongtran0309.releaseflow.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final IntegrationSourceRepository sourceRepository;
    private final Clock clock;

    ProjectService(
            ProjectRepository projectRepository,
            IntegrationSourceRepository sourceRepository,
            Clock clock
    ) {
        this.projectRepository = projectRepository;
        this.sourceRepository = sourceRepository;
        this.clock = clock;
    }

    @Transactional
    ProjectView create(UUID organizationId, ProjectRequest request) {
        Instant createdAt = clock.instant();
        Project project = projectRepository.save(new Project(
                UUID.randomUUID(),
                organizationId,
                request.getName(),
                createdAt
        ));
        return toView(project, List.of());
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
        return toView(
                project,
                sourceRepository.findAllByOrganizationIdAndProjectIdOrderByCreatedAtAscIdAsc(organizationId, projectId)
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UUID organizationId) {
        List<Project> projects = projectRepository.findAllByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<IntegrationSource>> sourcesByProject = sourceRepository
                .findAllByOrganizationIdAndProjectIdInOrderByCreatedAtAscIdAsc(
                        organizationId,
                        projects.stream().map(Project::getId).toList()
                )
                .stream()
                .collect(Collectors.groupingBy(IntegrationSource::getProjectId));

        return projects.stream()
                .map(project -> toView(project, sourcesByProject.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    private static ProjectView toView(Project project, List<IntegrationSource> sources) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                sources.stream().map(IntegrationSourceService::view).toList()
        );
    }
}
