package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization/slug")
public class OrganizationSlugApiController {

    private final OrganizationSlugService slugService;

    OrganizationSlugApiController(OrganizationSlugService slugService) {
        this.slugService = slugService;
    }

    @GetMapping
    OrganizationSlugView get(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return new OrganizationSlugView(slugService.slug(principal.organizationId()).value());
    }

    // Administrator only, by URL rule: it moves the Organization's public changelog.
    @PutMapping
    OrganizationSlugView change(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @RequestBody OrganizationSlugRequest request
    ) {
        return new OrganizationSlugView(slugService.change(principal.organizationId(), request.getSlug()).value());
    }

    record OrganizationSlugView(String slug) {
    }
}
