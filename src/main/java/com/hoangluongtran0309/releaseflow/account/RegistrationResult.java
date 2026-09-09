package com.hoangluongtran0309.releaseflow.account;

import java.util.UUID;

public record RegistrationResult(
        UUID organizationId,
        String organizationName,
        UUID userId,
        String email,
        String displayName,
        AppUserRole role
) {
}
