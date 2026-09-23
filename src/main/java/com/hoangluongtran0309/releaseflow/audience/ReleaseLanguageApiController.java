package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Members read the release note languages; only administrators change them, by URL rule.
@RestController
public class ReleaseLanguageApiController {

    private final ReleaseLanguageService releaseLanguageService;

    ReleaseLanguageApiController(ReleaseLanguageService releaseLanguageService) {
        this.releaseLanguageService = releaseLanguageService;
    }

    @GetMapping("/api/organization/release-languages")
    ReleaseLanguageSettings settings(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return releaseLanguageService.settings(principal.organizationId());
    }

    @PutMapping("/api/organization/release-languages")
    ReleaseLanguageSettings replace(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody ReleaseLanguagesRequest request
    ) {
        return releaseLanguageService.replace(principal, request);
    }
}
