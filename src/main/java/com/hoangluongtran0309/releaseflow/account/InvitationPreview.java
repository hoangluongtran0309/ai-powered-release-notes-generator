package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;

public record InvitationPreview(String email, String organizationName, Instant expiresAt) {
}
