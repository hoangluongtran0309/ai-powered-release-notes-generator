package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/releases")
public class ReleaseApiController {

    private final ReleaseService releaseService;

    ReleaseApiController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    List<ReleaseSummary> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID projectId) {
        return releaseService.list(principal.organizationId(), projectId);
    }

    @PostMapping
    ResponseEntity<ReleaseView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody ReleaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(releaseService.createDraft(principal.organizationId(), projectId, request));
    }

    @GetMapping("/{releaseId}")
    ReleaseView get(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.get(principal.organizationId(), projectId, releaseId);
    }

    @PutMapping("/{releaseId}")
    ReleaseView edit(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @RequestBody ReleaseRequest request
    ) {
        return releaseService.edit(principal.organizationId(), projectId, releaseId, request);
    }

    @DeleteMapping("/{releaseId}")
    ResponseEntity<Void> discard(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        releaseService.discard(principal.organizationId(), projectId, releaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{releaseId}/available-changes")
    List<ChangeView> availableChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.availableChanges(principal.organizationId(), projectId, releaseId);
    }

    @PostMapping("/{releaseId}/changes")
    ReleaseView addChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @RequestBody ReleaseChangesRequest request
    ) {
        return releaseService.addChanges(principal.organizationId(), projectId, releaseId, request);
    }

    @DeleteMapping("/{releaseId}/changes/{changeId}")
    ReleaseView removeChange(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId
    ) {
        return releaseService.removeChange(principal.organizationId(), projectId, releaseId, changeId);
    }
}
