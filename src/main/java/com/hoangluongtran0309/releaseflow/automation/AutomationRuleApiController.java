package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Administrator only, by URL rule: a rule delivers the Organization's release notes.
@RestController
@RequestMapping("/api/automation/rules")
public class AutomationRuleApiController {

    private final AutomationRuleService ruleService;
    private final AutomationRunService runService;

    AutomationRuleApiController(AutomationRuleService ruleService, AutomationRunService runService) {
        this.ruleService = ruleService;
        this.runService = runService;
    }

    @GetMapping
    List<AutomationRuleView> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return ruleService.list(principal.organizationId());
    }

    @GetMapping("/{ruleId}")
    AutomationRuleView get(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID ruleId) {
        return ruleService.get(principal.organizationId(), ruleId);
    }

    // A webhook rule's secret is in this response and in no other.
    @PostMapping
    ResponseEntity<AutomationRuleView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody AutomationRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ruleService.create(principal.organizationId(), request));
    }

    @PutMapping("/{ruleId}")
    ResponseEntity<AutomationRuleView> update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody AutomationRuleRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ruleService.update(principal.organizationId(), ruleId, request));
    }

    /** When a schedule would next fire, so a person can check what they wrote. */
    @PostMapping("/cron-preview")
    CronPreviewResponse previewCron(@Valid @RequestBody CronPreviewRequest request) {
        return new CronPreviewResponse(
                ruleService.previewCron(request.getCronExpression(), request.getCronTimeZone()));
    }

    // The new secret is in this response only; the path stays the same.
    @PostMapping("/{ruleId}/webhook-secret/rotate")
    ResponseEntity<AutomationRuleView> rotateWebhookSecret(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ruleService.rotateWebhookSecret(principal.organizationId(), ruleId));
    }


    @PostMapping("/{ruleId}/enable")
    AutomationRuleView enable(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID ruleId) {
        return ruleService.enable(principal.organizationId(), ruleId);
    }

    @PostMapping("/{ruleId}/disable")
    AutomationRuleView disable(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID ruleId) {
        return ruleService.disable(principal.organizationId(), ruleId);
    }

    // Archiving keeps the rule's runs and cancels the ones that had not finished.
    @DeleteMapping("/{ruleId}")
    ResponseEntity<Void> archive(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId
    ) {
        ruleService.archive(principal.organizationId(), ruleId);
        return ResponseEntity.noContent().build();
    }

    // Queues a run of the rule; the worker delivers it.
    @PostMapping("/{ruleId}/execute")
    ResponseEntity<AutomationRunView> execute(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody ExecuteRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runService.execute(principal, ruleId, request));
    }

    record CronPreviewResponse(Instant nextFireAt) {
    }
}
