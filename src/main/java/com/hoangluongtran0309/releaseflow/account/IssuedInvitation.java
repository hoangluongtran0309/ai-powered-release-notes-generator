package com.hoangluongtran0309.releaseflow.account;

import java.time.Instant;
import java.util.UUID;

/**
 * The only place a raw invitation token appears: returned once to the administrator.
 */
public record IssuedInvitation(UUID id, String email, String acceptancePath, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedInvitation[id=%s, email=%s, acceptancePath=[REDACTED], expiresAt=%s]"
                .formatted(id, email, expiresAt);
    }
}
