package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Members read how imports stand; only administrators start or resume one, by URL rule.
@RestController
public class SourceImportApiController {

    private final SourceImportService importService;

    SourceImportApiController(SourceImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/api/projects/{projectId}/imports")
    List<SourceSyncView> imports(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID projectId) {
        return importService.status(principal.organizationId(), projectId);
    }

    // Queues an import of the last 90 days; the worker runs it.
    @PostMapping("/api/projects/{projectId}/sources/{sourceId}/imports")
    ResponseEntity<SourceSyncView> startImport(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(importService.startImport(principal, projectId, sourceId));
    }

    @PostMapping("/api/projects/{projectId}/sources/{sourceId}/imports/resume")
    ResponseEntity<SourceSyncView> resume(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(importService.resume(principal, projectId, sourceId));
    }
}
