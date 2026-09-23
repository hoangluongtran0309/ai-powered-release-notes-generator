package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization/output-language")
public class OutputLanguageApiController {

    private final OutputLanguageService outputLanguageService;

    OutputLanguageApiController(OutputLanguageService outputLanguageService) {
        this.outputLanguageService = outputLanguageService;
    }

    @GetMapping
    OutputLanguageSettings get(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return outputLanguageService.settings(principal.organizationId());
    }

    // Administrator only, by URL rule.
    @PutMapping
    OutputLanguageSettings change(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody OutputLanguageRequest request
    ) {
        return outputLanguageService.change(principal.organizationId(), request.getOutputLanguage());
    }
}
