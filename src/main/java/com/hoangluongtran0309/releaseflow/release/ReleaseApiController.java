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
@RequestMapping("/api/projects/{projectId}")
public class ReleaseApiController {

    private final ReleaseService releaseService;

    ReleaseApiController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/releases")
    List<ReleaseSummary> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID projectId) {
        return releaseService.list(principal.organizationId(), projectId);
    }

    @PostMapping("/releases")
    ResponseEntity<ReleaseView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody NewReleaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(releaseService.createDraft(principal.organizationId(), projectId, request));
    }

    @GetMapping("/releases/{releaseId}")
    ReleaseView get(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.get(principal.organizationId(), projectId, releaseId);
    }

    @PutMapping("/releases/{releaseId}")
    ReleaseView edit(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @RequestBody ReleaseRequest request
    ) {
        return releaseService.edit(principal.organizationId(), projectId, releaseId, request);
    }

    @DeleteMapping("/releases/{releaseId}")
    ResponseEntity<Void> discard(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        releaseService.discard(principal.organizationId(), projectId, releaseId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/releases/{releaseId}/schedule")
    ReleaseView schedule(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @RequestBody ReleaseScheduleRequest request
    ) {
        return releaseService.schedule(principal.organizationId(), projectId, releaseId, request);
    }

    @GetMapping("/releases/{releaseId}/available-changes")
    List<ChangeView> availableChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.availableChanges(principal.organizationId(), projectId, releaseId);
    }

    @PostMapping("/releases/{releaseId}/changes")
    ReleaseView addChanges(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @Valid @RequestBody ReleaseChangesRequest request
    ) {
        return releaseService.addChanges(principal.organizationId(), projectId, releaseId, request);
    }

    // Removes a change from a draft, or rejects it while the release is in review.
    @DeleteMapping("/releases/{releaseId}/changes/{changeId}")
    ReleaseView removeChange(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId
    ) {
        return releaseService.removeChange(principal.organizationId(), projectId, releaseId, changeId);
    }

    @PostMapping("/releases/{releaseId}/request-review")
    ReleaseView requestReview(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.requestReview(principal.organizationId(), projectId, releaseId);
    }

    @PutMapping("/releases/{releaseId}/changes/{changeId}/decision")
    ReleaseView decide(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId,
            @PathVariable UUID changeId,
            @Valid @RequestBody ReleaseDecisionRequest request
    ) {
        return releaseService.decide(principal, projectId, releaseId, changeId, request);
    }

    @PostMapping("/releases/{releaseId}/approve")
    ReleaseView approve(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.approve(principal, projectId, releaseId);
    }

    @PostMapping("/releases/{releaseId}/return-to-draft")
    ReleaseView returnToDraft(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.returnToDraft(principal.organizationId(), projectId, releaseId);
    }

    @PostMapping("/releases/{releaseId}/publish")
    ReleaseView publish(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID releaseId
    ) {
        return releaseService.publish(principal, projectId, releaseId);
    }

    @GetMapping("/release-assignments")
    List<ReleaseAssignment> assignments(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return releaseService.assignments(principal.organizationId(), projectId);
    }
}
