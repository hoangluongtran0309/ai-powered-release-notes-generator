package com.hoangluongtran0309.releaseflow.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/session")
public class SessionApiController {

    @GetMapping
    public SessionResponse current(@AuthenticationPrincipal ReleaseFlowPrincipal principal) {
        return new SessionResponse(
                principal.userId(),
                principal.organizationId(),
                principal.getUsername(),
                principal.displayName(),
                principal.role()
        );
    }

    public record SessionResponse(
            UUID userId,
            UUID organizationId,
            String email,
            String displayName,
            AppUserRole role
    ) {
    }
}
