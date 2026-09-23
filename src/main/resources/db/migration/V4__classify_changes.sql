-- Changes recorded before rule-based classification existed are not guessed at:
-- they become Unknown and wait for human review.
ALTER TABLE changes
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN breaking BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN classification_reasons TEXT[] NOT NULL
        DEFAULT ARRAY['Recorded before rule-based classification'];

ALTER TABLE changes
    ALTER COLUMN category DROP DEFAULT,
    ALTER COLUMN breaking DROP DEFAULT,
    ALTER COLUMN needs_review DROP DEFAULT,
    ALTER COLUMN classification_reasons DROP DEFAULT,
    ADD CONSTRAINT changes_category_known CHECK (
        category IN ('FEATURE', 'FIX', 'PERFORMANCE', 'DOCUMENTATION', 'MAINTENANCE', 'UNKNOWN')
    ),
    ADD CONSTRAINT changes_review_required CHECK (
        needs_review OR (NOT breaking AND category <> 'UNKNOWN')
    );

CREATE INDEX changes_inbox_idx
    ON changes (organization_id, project_id, merged_at DESC, id);
