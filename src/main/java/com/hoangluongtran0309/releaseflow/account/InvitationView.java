package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

public record InvitationView(
        UUID id,
        String email,
        InvitationStatus status,
        Instant expiresAt,
        String createdByName,
        Instant createdAt
) {
}
