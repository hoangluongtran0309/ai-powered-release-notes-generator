package com.hoangluongtran0309.releaseflow.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final GitHubIntegrationRepository integrationRepository;
    private final Clock clock;

    ProjectService(
            ProjectRepository projectRepository,
            GitHubIntegrationRepository integrationRepository,
            Clock clock
    ) {
        this.projectRepository = projectRepository;
        this.integrationRepository = integrationRepository;
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
        return toView(project, null);
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(ProjectNotFoundException::new);
        return toView(
                project,
                integrationRepository.findByProjectIdAndOrganizationId(projectId, organizationId).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UUID organizationId) {
        List<Project> projects = projectRepository.findAllByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<UUID, GitHubIntegration> integrationsByProject = integrationRepository
                .findAllByOrganizationIdAndProjectIdIn(
                        organizationId,
                        projects.stream().map(Project::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(GitHubIntegration::getProjectId, Function.identity()));

        return projects.stream()
                .map(project -> toView(project, integrationsByProject.get(project.getId())))
                .toList();
    }

    private static ProjectView toView(Project project, GitHubIntegration integration) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                integration == null ? null : new GitHubIntegrationView(
                        integration.getId(),
                        integration.getRepositoryOwner(),
                        integration.getRepositoryName(),
                        integration.getWebhookId(),
                        GitHubIntegrationService.webhookPath(integration.getWebhookId()),
                        integration.getCreatedAt(),
                        integration.getLastDeliveryAt()
                )
        );
    }
}
