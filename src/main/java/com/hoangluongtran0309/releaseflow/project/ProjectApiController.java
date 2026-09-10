package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectApiController {

    private final ProjectService projectService;
    private final GitHubIntegrationService integrationService;

    ProjectApiController(ProjectService projectService, GitHubIntegrationService integrationService) {
        this.projectService = projectService;
        this.integrationService = integrationService;
    }

    @GetMapping
    List<ProjectView> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return projectService.list(principal.organizationId());
    }

    @PostMapping
    ResponseEntity<ProjectView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody ProjectRequest request
    ) {
        ProjectView created = projectService.create(principal.organizationId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{projectId}/github-integration")
    ResponseEntity<GitHubIntegrationCreated> configureGitHub(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody GitHubIntegrationRequest request
    ) {
        GitHubIntegrationCreated created = integrationService.configure(
                principal.organizationId(),
                projectId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }
}
