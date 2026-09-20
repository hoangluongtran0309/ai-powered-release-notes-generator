package com.hoangluongtran0309.releaseflow.automation;

import java.util.Map;
import java.util.UUID;

/**
 * Everything one delivery needs, copied out of the database when the Action was
 * claimed, so the provider is called with no transaction open. Never log this: it
 * carries the decrypted secret.
 */
record ActionCommand(
        UUID actionRunId,
        UUID organizationId,
        UUID projectId,
        UUID releaseId,
        String releaseVersion,
        String audienceName,
        String language,
        String noteContent,
        Map<String, String> configuration,
        String rawSecret
) {

    ActionCommand {
        configuration = Map.copyOf(configuration);
    }

    @Override
    public String toString() {
        return "ActionCommand[actionRunId=%s, release=%s, secret=***]".formatted(actionRunId, releaseId);
    }
}
