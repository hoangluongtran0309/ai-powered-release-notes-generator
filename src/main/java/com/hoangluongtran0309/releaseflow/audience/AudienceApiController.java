package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
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

// Administrator only, by URL rule.
@RestController
@RequestMapping("/api/audiences")
public class AudienceApiController {

    private final AudienceService audienceService;

    AudienceApiController(AudienceService audienceService) {
        this.audienceService = audienceService;
    }

    @GetMapping
    List<AudienceView> list(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return audienceService.list(principal.organizationId());
    }

    @PostMapping
    ResponseEntity<AudienceView> create(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody NewAudienceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(audienceService.create(principal.organizationId(), request));
    }

    @GetMapping("/{audienceId}")
    AudienceView get(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID audienceId) {
        return audienceService.get(principal.organizationId(), audienceId);
    }

    @PutMapping("/{audienceId}")
    AudienceView update(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID audienceId,
            @Valid @RequestBody AudienceRequest request
    ) {
        return audienceService.update(principal.organizationId(), audienceId, request);
    }

    @DeleteMapping("/{audienceId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID audienceId) {
        audienceService.delete(principal.organizationId(), audienceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{audienceId}/reset-to-preset")
    AudienceView resetToPreset(@AuthenticationPrincipal ReleaseFlowPrincipal principal, @PathVariable UUID audienceId) {
        return audienceService.resetToPreset(principal.organizationId(), audienceId);
    }

    @PostMapping("/preview")
    AudiencePreview preview(@RequestBody AudiencePreviewRequest request) {
        return audienceService.preview(request.getTemplateBody());
    }
}
