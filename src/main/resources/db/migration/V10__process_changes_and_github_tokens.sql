-- An optional, write-only GitHub access token per integration, encrypted like the
-- webhook secret.
ALTER TABLE github_integrations
    ADD COLUMN token_nonce BYTEA,
    ADD COLUMN token_ciphertext BYTEA,
    ADD COLUMN token_updated_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT github_integrations_token_complete CHECK (
        (token_nonce IS NULL) = (token_ciphertext IS NULL)
        AND (token_nonce IS NULL) = (token_updated_at IS NULL)
    ),
    ADD CONSTRAINT github_integrations_token_nonce_length
        CHECK (token_nonce IS NULL OR OCTET_LENGTH(token_nonce) = 12),
    ADD CONSTRAINT github_integrations_token_ciphertext_length
        CHECK (token_ciphertext IS NULL OR OCTET_LENGTH(token_ciphertext) > 16);

-- Changes recorded before changed-file collection keep their classification and
-- are never processed again.
ALTER TABLE changes
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN changed_file_status VARCHAR(20),
    ADD COLUMN changed_files JSONB,
    ADD COLUMN review_triggers JSONB NOT NULL DEFAULT '[]';

-- A change still being processed cannot be settled, reviewed, or suggested by AI.
-- Review triggers only ever add a need for review; only a recorded review clears it.
ALTER TABLE changes
    ALTER COLUMN processing_status DROP DEFAULT,
    ALTER COLUMN review_triggers DROP DEFAULT,
    ADD CONSTRAINT changes_processing_status_known
        CHECK (processing_status IN ('PROCESSING', 'COMPLETED')),
    ADD CONSTRAINT changes_changed_file_status_known
        CHECK (changed_file_status IN ('COLLECTED', 'UNAVAILABLE')),
    ADD CONSTRAINT changes_changed_files_consistent CHECK (
        (changed_file_status IS NOT DISTINCT FROM 'COLLECTED') = (changed_files IS NOT NULL)
        AND (changed_files IS NULL OR jsonb_typeof(changed_files) = 'array')
    ),
    ADD CONSTRAINT changes_processing_unsettled CHECK (
        processing_status = 'COMPLETED'
        OR (
            needs_review
            AND reviewed_at IS NULL
            AND classification_source = 'RULES'
            AND ai_status = 'NOT_REQUESTED'
            AND changed_file_status IS NULL
            AND review_triggers = '[]'::jsonb
        )
    ),
    ADD CONSTRAINT changes_review_triggers_array CHECK (jsonb_typeof(review_triggers) = 'array'),
    ADD CONSTRAINT changes_triggers_require_review CHECK (
        review_triggers = '[]'::jsonb OR needs_review OR reviewed_at IS NOT NULL
    );

CREATE TABLE change_processing_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    change_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT change_processing_jobs_change_unique UNIQUE (change_id),
    CONSTRAINT change_processing_jobs_change_fk
        FOREIGN KEY (change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id),
    CONSTRAINT change_processing_jobs_status_known
        CHECK (status IN ('PENDING', 'ENRICHING', 'COMPLETED')),
    CONSTRAINT change_processing_jobs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT change_processing_jobs_state_consistent CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL)
        AND (status <> 'ENRICHING' OR claimed_at IS NOT NULL)
    )
);

CREATE INDEX change_processing_jobs_pending_idx
    ON change_processing_jobs (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX change_processing_jobs_enriching_idx
    ON change_processing_jobs (claimed_at)
    WHERE status = 'ENRICHING';
