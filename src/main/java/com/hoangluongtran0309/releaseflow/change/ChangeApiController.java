package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ChangeApiController {

    private final ChangeInboxService inboxService;
    private final ChangeAiClassificationService aiClassificationService;
    private final ChangeReviewService reviewService;

    ChangeApiController(
            ChangeInboxService inboxService,
            ChangeAiClassificationService aiClassificationService,
            ChangeReviewService reviewService
    ) {
        this.inboxService = inboxService;
        this.aiClassificationService = aiClassificationService;
        this.reviewService = reviewService;
    }

    @GetMapping("/api/projects/{projectId}/changes")
    List<ChangeView> list(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status
    ) {
        return inboxService.list(principal.organizationId(), projectId, ChangeFilter.parse(category, status));
    }

    @PostMapping("/api/projects/{projectId}/changes/{changeId}/review")
    ChangeView review(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId,
            @Valid @RequestBody ChangeReviewRequest request
    ) {
        return reviewService.review(principal, projectId, changeId, request);
    }

    @PostMapping("/api/projects/{projectId}/changes/{changeId}/ai-classification")
    ChangeView classifyWithAi(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID changeId
    ) {
        return aiClassificationService.classify(principal.organizationId(), projectId, changeId);
    }
}
