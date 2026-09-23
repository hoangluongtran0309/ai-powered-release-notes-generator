package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Administrator only, by URL rule: a run history names where notes were delivered.
@RestController
@RequestMapping("/api/automation/runs")
public class AutomationRunApiController {

    private final AutomationRunService runService;

    AutomationRunApiController(AutomationRunService runService) {
        this.runService = runService;
    }

    @GetMapping
    AutomationRunPage list(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return runService.list(principal.organizationId(), page, size);
    }

    @GetMapping("/{runId}")
    AutomationRunView get(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID runId) {
        return runService.get(principal.organizationId(), runId);
    }

    @PostMapping("/{runId}/retry")
    AutomationRunView retry(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID runId,
            @RequestBody(required = false) RetryRunRequest request
    ) {
        return runService.retry(principal.organizationId(), runId, request != null && request.isConfirmUnknown());
    }

    @PostMapping("/{runId}/cancel")
    AutomationRunView cancel(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID runId) {
        return runService.cancel(principal.organizationId(), runId);
    }
}
