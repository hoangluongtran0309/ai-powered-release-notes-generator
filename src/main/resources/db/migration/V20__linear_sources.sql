-- Linear becomes the third kind of integration source (ADR-0018). A Linear team is an
-- issue tracker: its changes have no merge commit and no target branch, it has no changed
-- files at all, and it keeps no history ReleaseFlow could import. GitHub and GitLab rows
-- are untouched, and a change of theirs still must carry what a code host always has.

ALTER TABLE integration_sources
    ADD COLUMN external_workspace_key VARCHAR(100);

ALTER TABLE integration_sources
    DROP CONSTRAINT integration_sources_type_known,
    ADD CONSTRAINT integration_sources_type_known CHECK (source_type IN ('GITHUB', 'GITLAB', 'LINEAR')),
    DROP CONSTRAINT integration_sources_auth_mode_known,
    ADD CONSTRAINT integration_sources_auth_mode_known CHECK (
        webhook_auth_mode IN ('GITHUB_HMAC', 'GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN', 'LINEAR_HMAC')
    ),
    DROP CONSTRAINT integration_sources_auth_mode_matches_type,
    ADD CONSTRAINT integration_sources_auth_mode_matches_type CHECK (
        (source_type = 'GITHUB' AND webhook_auth_mode = 'GITHUB_HMAC')
        OR (source_type = 'GITLAB' AND webhook_auth_mode IN ('GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN'))
        OR (source_type = 'LINEAR' AND webhook_auth_mode = 'LINEAR_HMAC')
    ),
    -- Only a provider whose projects live inside a workspace names one.
    ADD CONSTRAINT integration_sources_workspace_present CHECK (
        (source_type = 'LINEAR') = (external_workspace_key IS NOT NULL)
    ),
    -- A change may state its source's type, so that type must be readable from the source.
    ADD CONSTRAINT integration_sources_id_type_unique UNIQUE (id, source_type);

-- Only a code host's change has a merge commit and a branch; an issue tracker's has
-- neither, and may not even name a creator.
ALTER TABLE changes
    ALTER COLUMN merge_commit_sha DROP NOT NULL,
    ALTER COLUMN target_branch DROP NOT NULL,
    ALTER COLUMN author_login DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(20);

UPDATE changes c
SET source_type = s.source_type
FROM integration_sources s
WHERE s.id = c.source_id;

ALTER TABLE changes
    DROP CONSTRAINT changes_merge_commit_sha_format,
    ADD CONSTRAINT changes_merge_commit_sha_format CHECK (
        merge_commit_sha IS NULL OR merge_commit_sha ~ '^([0-9a-f]{40}|[0-9a-f]{64})$'
    ),
    -- A change recorded before V18 has no source at all, and so states no type.
    ADD CONSTRAINT changes_source_type_present CHECK ((source_id IS NULL) = (source_type IS NULL)),
    ADD CONSTRAINT changes_source_type_matches
        FOREIGN KEY (source_id, source_type) REFERENCES integration_sources (id, source_type),
    ADD CONSTRAINT changes_commit_matches_type CHECK (
        source_type IS NULL
        OR (source_type IN ('GITHUB', 'GITLAB'))
            = (merge_commit_sha IS NOT NULL AND target_branch IS NOT NULL)
    );

-- A source that cannot report files at all is not a source whose files failed to arrive.
ALTER TABLE changes
    DROP CONSTRAINT changes_changed_file_status_known,
    ADD CONSTRAINT changes_changed_file_status_known
        CHECK (changed_file_status IN ('COLLECTED', 'UNAVAILABLE', 'NOT_SUPPORTED'));
