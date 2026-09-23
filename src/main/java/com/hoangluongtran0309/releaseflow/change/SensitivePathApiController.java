package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Members read a Project's sensitive paths; only administrators change them, by URL rule.
@RestController
public class SensitivePathApiController {

    private final ProjectSensitivePathService sensitivePathService;

    SensitivePathApiController(ProjectSensitivePathService sensitivePathService) {
        this.sensitivePathService = sensitivePathService;
    }

    @GetMapping("/api/projects/{projectId}/sensitive-paths")
    SensitivePathsView view(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID projectId) {
        return sensitivePathService.view(principal.organizationId(), projectId);
    }

    // Replaces the Project's additions; the baseline always stays.
    @PutMapping("/api/projects/{projectId}/sensitive-paths")
    SensitivePathsView replace(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody SensitivePathsRequest request
    ) {
        return sensitivePathService.replace(principal, projectId, request);
    }
}
