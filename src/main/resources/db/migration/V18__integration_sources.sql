-- Integration sources and historical import (ADR-0016). A Project may have several
-- sources, a change is identified by its source, and merged pull requests can be
-- imported from a source's history.

-- GitHub integrations become the first kind of integration source. Rows keep their
-- IDs, webhook IDs, repository names, and ciphertexts, so the authenticated data of
-- every encrypted secret and token is unchanged and existing webhook paths still work.
ALTER TABLE github_integrations RENAME TO integration_sources;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_pkey TO integration_sources_pkey;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_project_organization_fk
    TO integration_sources_project_organization_fk;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_webhook_id_unique
    TO integration_sources_webhook_id_unique;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_owner_canonical
    TO integration_sources_owner_canonical;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_repository_canonical
    TO integration_sources_repository_canonical;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_nonce_length TO integration_sources_nonce_length;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_ciphertext_length
    TO integration_sources_ciphertext_length;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_token_complete TO integration_sources_token_complete;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_token_nonce_length
    TO integration_sources_token_nonce_length;
ALTER TABLE integration_sources RENAME CONSTRAINT github_integrations_token_ciphertext_length
    TO integration_sources_token_ciphertext_length;

ALTER TABLE integration_sources
    DROP CONSTRAINT github_integrations_project_unique,
    DROP CONSTRAINT github_integrations_repository_unique,
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'GITHUB',
    ADD COLUMN external_project_key VARCHAR(200),
    ADD COLUMN connection_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN last_sync_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_error_code VARCHAR(100);

UPDATE integration_sources SET external_project_key = repository_owner || '/' || repository_name;

ALTER TABLE integration_sources
    ALTER COLUMN source_type DROP DEFAULT,
    ALTER COLUMN external_project_key SET NOT NULL,
    ADD CONSTRAINT integration_sources_type_known CHECK (source_type IN ('GITHUB')),
    ADD CONSTRAINT integration_sources_github_key CHECK (
        source_type <> 'GITHUB' OR external_project_key = repository_owner || '/' || repository_name
    ),
    ADD CONSTRAINT integration_sources_connection_status_known CHECK (connection_status IN ('ACTIVE', 'ERROR')),
    ADD CONSTRAINT integration_sources_external_unique UNIQUE (organization_id, source_type, external_project_key),
    ADD CONSTRAINT integration_sources_id_tenant_project_unique UNIQUE (id, organization_id, project_id);

CREATE INDEX integration_sources_project_idx ON integration_sources (organization_id, project_id, created_at);

-- A change is identified by its source and the source's own ID for it; for GitHub that
-- is the pull request number. Webhook deliveries carry a delivery ID, imports do not.
ALTER TABLE changes
    ADD COLUMN source_id UUID,
    ADD COLUMN external_id VARCHAR(100),
    ADD COLUMN origin VARCHAR(10) NOT NULL DEFAULT 'WEBHOOK';

UPDATE changes c
SET source_id = s.id,
    external_id = c.pull_request_number::text
FROM integration_sources s
WHERE s.project_id = c.project_id AND s.organization_id = c.organization_id;

ALTER TABLE changes
    DROP CONSTRAINT changes_project_pull_request_unique,
    ALTER COLUMN delivery_id DROP NOT NULL,
    ADD CONSTRAINT changes_source_fk
        FOREIGN KEY (source_id, organization_id, project_id)
        REFERENCES integration_sources (id, organization_id, project_id),
    ADD CONSTRAINT changes_source_identified CHECK ((source_id IS NULL) = (external_id IS NULL)),
    ADD CONSTRAINT changes_external_id_not_blank CHECK (external_id IS NULL OR BTRIM(external_id) <> ''),
    ADD CONSTRAINT changes_source_external_unique UNIQUE (source_id, external_id),
    ADD CONSTRAINT changes_origin_known CHECK (origin IN ('WEBHOOK', 'IMPORT')),
    ADD CONSTRAINT changes_delivery_matches_origin CHECK ((origin = 'WEBHOOK') = (delivery_id IS NOT NULL));

-- Durable work that reads a source's history. Only one job per source may be under
-- way at a time.
CREATE TABLE source_sync_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    source_id UUID NOT NULL,
    job_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    provider_cursor VARCHAR(100) NOT NULL,
    scanned_count INTEGER NOT NULL,
    imported_count INTEGER NOT NULL,
    item_limit INTEGER NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    requested_by UUID NOT NULL,
    requester_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT source_sync_jobs_source_fk
        FOREIGN KEY (source_id, organization_id, project_id)
        REFERENCES integration_sources (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT source_sync_jobs_requester_fk
        FOREIGN KEY (requested_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT source_sync_jobs_type_known CHECK (job_type IN ('HISTORICAL_IMPORT')),
    CONSTRAINT source_sync_jobs_status_known CHECK (
        status IN ('PENDING', 'RUNNING', 'RETRY_SCHEDULED', 'COMPLETED', 'PARTIAL', 'FAILED')
    ),
    CONSTRAINT source_sync_jobs_window_ordered CHECK (window_start < window_end),
    CONSTRAINT source_sync_jobs_counts_valid CHECK (
        scanned_count >= 0 AND imported_count >= 0 AND imported_count <= item_limit AND item_limit > 0 AND attempts >= 0
    ),
    CONSTRAINT source_sync_jobs_completion_recorded CHECK (
        (status IN ('COMPLETED', 'PARTIAL', 'FAILED')) = (completed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX source_sync_jobs_one_active
    ON source_sync_jobs (source_id)
    WHERE status IN ('PENDING', 'RUNNING', 'RETRY_SCHEDULED');

CREATE INDEX source_sync_jobs_due_idx ON source_sync_jobs (status, next_attempt_at);
CREATE INDEX source_sync_jobs_source_idx ON source_sync_jobs (source_id, created_at);
