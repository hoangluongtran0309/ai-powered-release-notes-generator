package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

public record MemberView(UUID id, String email, String displayName, AppUserRole role, Instant createdAt) {
}
