package com.hoangluongtran0309.releaseflow.account;

import java.util.UUID;

/**
 * Published inside the registration transaction, so a listener that fails rolls the
 * registration back. It lets other capabilities set up a new Organization without
 * {@code account} depending on them.
 */
public record OrganizationRegistered(UUID organizationId, OutputLanguage outputLanguage) {
}
