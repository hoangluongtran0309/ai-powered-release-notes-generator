package com.hoangluongtran0309.releaseflow.automation;

import java.util.UUID;

/**
 * One step of a rule as anyone may read it back. A stored secret is reported only as
 * present: no response, page, or log ever contains one.
 */
public record AutomationActionView(
        UUID id,
        int position,
        ActionType actionType,
        UUID audienceId,
        String audienceName,
        String language,
        String recipients,
        String parentPageId,
        String siteUrl,
        String email,
        String spaceId,
        boolean secretConfigured
) {
}
