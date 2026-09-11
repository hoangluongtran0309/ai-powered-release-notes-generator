package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ChangeApiController {

    private final ChangeInboxService inboxService;

    ChangeApiController(ChangeInboxService inboxService) {
        this.inboxService = inboxService;
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
}
