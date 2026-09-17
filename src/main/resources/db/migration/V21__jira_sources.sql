-- Jira becomes the fourth kind of integration source (ADR-0019), and the first nobody
-- delivers to: ReleaseFlow asks it on a schedule. It is also the first source that
-- explains other sources' changes, so a change now records what its Project's tracker
-- could add about the issues it mentions.

ALTER TABLE integration_sources
    ADD COLUMN credential_identity VARCHAR(255),
    ADD COLUMN poll_cursor_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN next_poll_at TIMESTAMP WITH TIME ZONE,
    -- Nothing is delivered to a polled source, so it is reachable at no address.
    ALTER COLUMN webhook_id DROP NOT NULL,
    ALTER COLUMN secret_nonce DROP NOT NULL,
    ALTER COLUMN secret_ciphertext DROP NOT NULL;

ALTER TABLE integration_sources
    DROP CONSTRAINT integration_sources_type_known,
    ADD CONSTRAINT integration_sources_type_known
        CHECK (source_type IN ('GITHUB', 'GITLAB', 'LINEAR', 'JIRA')),
    DROP CONSTRAINT integration_sources_auth_mode_known,
    ADD CONSTRAINT integration_sources_auth_mode_known CHECK (
        webhook_auth_mode IN ('GITHUB_HMAC', 'GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN', 'LINEAR_HMAC', 'NONE')
    ),
    DROP CONSTRAINT integration_sources_auth_mode_matches_type,
    ADD CONSTRAINT integration_sources_auth_mode_matches_type CHECK (
        (source_type = 'GITHUB' AND webhook_auth_mode = 'GITHUB_HMAC')
        OR (source_type = 'GITLAB' AND webhook_auth_mode IN ('GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN'))
        OR (source_type = 'LINEAR' AND webhook_auth_mode = 'LINEAR_HMAC')
        OR (source_type = 'JIRA' AND webhook_auth_mode = 'NONE')
    ),
    -- A Jira site is chosen the same way a GitLab instance is.
    DROP CONSTRAINT integration_sources_base_url_present,
    ADD CONSTRAINT integration_sources_base_url_present CHECK (
        (source_type IN ('GITLAB', 'JIRA')) = (api_base_url IS NOT NULL)
    ),
    -- Only a source ReleaseFlow can be delivered to has an address and a secret.
    ADD CONSTRAINT integration_sources_webhook_present CHECK (
        (webhook_auth_mode <> 'NONE') = (webhook_id IS NOT NULL)
    ),
    ADD CONSTRAINT integration_sources_secret_present CHECK (
        (webhook_id IS NOT NULL) = (secret_nonce IS NOT NULL AND secret_ciphertext IS NOT NULL)
    ),
    -- Only a polled source has a schedule, and only Jira signs in as an account.
    ADD CONSTRAINT integration_sources_poll_schedule CHECK (
        (source_type = 'JIRA') = (next_poll_at IS NOT NULL AND poll_cursor_at IS NOT NULL)
    ),
    ADD CONSTRAINT integration_sources_identity_present CHECK (
        (source_type = 'JIRA') = (credential_identity IS NOT NULL)
    );

CREATE INDEX integration_sources_due_poll_idx
    ON integration_sources (source_type, next_poll_at)
    WHERE next_poll_at IS NOT NULL;

-- A poll is the first work nobody asks for, so it names no requester. A provider that
-- pages with a token rather than a number needs more room for its cursor.
ALTER TABLE source_sync_jobs
    ALTER COLUMN requested_by DROP NOT NULL,
    ALTER COLUMN requester_name DROP NOT NULL,
    ALTER COLUMN provider_cursor TYPE VARCHAR(500),
    DROP CONSTRAINT source_sync_jobs_type_known,
    ADD CONSTRAINT source_sync_jobs_type_known CHECK (job_type IN ('HISTORICAL_IMPORT', 'JIRA_POLL')),
    ADD CONSTRAINT source_sync_jobs_requester_matches_type CHECK (
        (job_type = 'HISTORICAL_IMPORT') = (requested_by IS NOT NULL AND requester_name IS NOT NULL)
    );

-- What the Project's issue tracker could add about a change from another source.
ALTER TABLE changes
    ADD COLUMN linked_context_status VARCHAR(20),
    ADD COLUMN linked_issues JSONB;

ALTER TABLE changes
    ADD CONSTRAINT changes_linked_context_status_known CHECK (
        linked_context_status IN ('NOT_SUPPORTED', 'NOT_CONFIGURED', 'NOT_FOUND', 'PARTIAL', 'UNAVAILABLE', 'COLLECTED')
    ),
    -- Issues can only have been read when the lookup got far enough to read them.
    ADD CONSTRAINT changes_linked_issues_consistent CHECK (
        (linked_issues IS NULL OR jsonb_typeof(linked_issues) = 'array')
        AND (linked_issues IS NULL OR linked_context_status IN ('PARTIAL', 'UNAVAILABLE', 'COLLECTED'))
    );
