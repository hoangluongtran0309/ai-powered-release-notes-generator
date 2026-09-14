-- The language generated content is written in. Existing Organizations get English.
ALTER TABLE organizations
    ADD COLUMN output_language VARCHAR(16) NOT NULL DEFAULT 'en';

ALTER TABLE organizations
    ALTER COLUMN output_language DROP DEFAULT,
    ADD CONSTRAINT organizations_output_language_not_blank CHECK (BTRIM(output_language) <> '');

-- AI classification now runs automatically and may settle a change (ADR-0009).
-- It still never clears a breaking flag or settles an Unknown change, and review
-- triggers still require a person.
ALTER TABLE changes
    ADD COLUMN neutral_summary JSONB,
    ADD COLUMN content_language VARCHAR(16),
    ADD COLUMN ai_provider VARCHAR(20);

UPDATE changes SET ai_provider = 'openai' WHERE ai_status <> 'NOT_REQUESTED';

ALTER TABLE changes
    ADD CONSTRAINT changes_ai_provider_known CHECK (ai_provider IN ('openai', 'anthropic', 'deepseek')),
    ADD CONSTRAINT changes_ai_provider_recorded CHECK ((ai_provider IS NULL) = (ai_status = 'NOT_REQUESTED')),
    ADD CONSTRAINT changes_neutral_summary_consistent CHECK (
        (neutral_summary IS NULL) = (content_language IS NULL)
        AND (neutral_summary IS NULL OR (jsonb_typeof(neutral_summary) = 'object' AND ai_status = 'SUCCEEDED'))
    ),
    DROP CONSTRAINT changes_ai_state_consistent,
    ADD CONSTRAINT changes_ai_state_consistent CHECK (
        (classification_source <> 'AI' OR ai_status = 'SUCCEEDED')
        AND (ai_status <> 'SUCCEEDED' OR ai_model IS NOT NULL)
        AND ((ai_status = 'FAILED') = (ai_failure IS NOT NULL))
        AND ((ai_status = 'NOT_REQUESTED') = (ai_attempted_at IS NULL))
    ),
    -- Changed files are stored before the AI call, so a Processing change may carry
    -- them; it still cannot be classified, triggered, reviewed, or sent to AI.
    DROP CONSTRAINT changes_processing_unsettled,
    ADD CONSTRAINT changes_processing_unsettled CHECK (
        processing_status = 'COMPLETED'
        OR (
            needs_review
            AND category = 'UNKNOWN'
            AND reviewed_at IS NULL
            AND classification_source = 'RULES'
            AND ai_status = 'NOT_REQUESTED'
            AND review_triggers = '[]'::jsonb
            AND neutral_summary IS NULL
        )
    );

-- CLASSIFYING marks a job whose single AI call may be in flight; a stale one becomes
-- FALLBACK_REQUIRED and is completed without calling the AI again.
ALTER TABLE change_processing_jobs
    DROP CONSTRAINT change_processing_jobs_status_known,
    ADD CONSTRAINT change_processing_jobs_status_known
        CHECK (status IN ('PENDING', 'ENRICHING', 'CLASSIFYING', 'FALLBACK_REQUIRED', 'COMPLETED')),
    DROP CONSTRAINT change_processing_jobs_state_consistent,
    ADD CONSTRAINT change_processing_jobs_state_consistent CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL)
        AND (status NOT IN ('ENRICHING', 'CLASSIFYING') OR claimed_at IS NOT NULL)
    );

DROP INDEX change_processing_jobs_pending_idx;
CREATE INDEX change_processing_jobs_due_idx
    ON change_processing_jobs (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FALLBACK_REQUIRED');

DROP INDEX change_processing_jobs_enriching_idx;
CREATE INDEX change_processing_jobs_claimed_idx
    ON change_processing_jobs (claimed_at)
    WHERE status IN ('ENRICHING', 'CLASSIFYING');
