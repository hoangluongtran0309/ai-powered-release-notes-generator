package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/invitations")
public class PublicInvitationApiController {

    private final InvitationService invitationService;

    PublicInvitationApiController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/inspect")
    ResponseEntity<InvitationPreview> inspect(@Valid @RequestBody InvitationTokenRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitationService.inspect(request.getToken()));
    }

    @PostMapping("/accept")
    ResponseEntity<AcceptedInvitation> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(invitationService.accept(request));
    }
}
