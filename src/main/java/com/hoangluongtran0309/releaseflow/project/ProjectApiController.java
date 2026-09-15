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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Members read Projects and their sources; connecting a source and setting its token
// are administrator only, by URL rule.
@RestController
@RequestMapping("/api/projects")
public class ProjectApiController {

    private final ProjectService projectService;
    private final IntegrationSourceService sourceService;

    ProjectApiController(ProjectService projectService, IntegrationSourceService sourceService) {
        this.projectService = projectService;
        this.sourceService = sourceService;
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

    @GetMapping("/{projectId}/sources")
    List<IntegrationSourceView> sources(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return sourceService.list(principal.organizationId(), projectId);
    }

    // The webhook secret is in this response only.
    @PostMapping("/{projectId}/sources")
    ResponseEntity<IntegrationSourceCreated> createSource(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody IntegrationSourceRequest request
    ) {
        IntegrationSourceCreated created = sourceService.create(principal.organizationId(), projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PutMapping("/{projectId}/sources/{sourceId}/token")
    ResponseEntity<Void> replaceToken(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId,
            @Valid @RequestBody GitHubTokenRequest request
    ) {
        sourceService.replaceToken(principal.organizationId(), projectId, sourceId, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
