ALTER TABLE changes
    ADD COLUMN classification_source VARCHAR(10) NOT NULL DEFAULT 'RULES',
    ADD COLUMN ai_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN ai_model VARCHAR(100),
    ADD COLUMN ai_failure VARCHAR(300),
    ADD COLUMN ai_attempted_at TIMESTAMP WITH TIME ZONE;

-- An AI result is only ever a suggestion: it always leaves the change needing review.
ALTER TABLE changes
    ALTER COLUMN classification_source DROP DEFAULT,
    ALTER COLUMN ai_status DROP DEFAULT,
    ADD CONSTRAINT changes_classification_source_known CHECK (classification_source IN ('RULES', 'AI')),
    ADD CONSTRAINT changes_ai_status_known CHECK (ai_status IN ('NOT_REQUESTED', 'SUCCEEDED', 'FAILED')),
    ADD CONSTRAINT changes_ai_state_consistent CHECK (
        (classification_source = 'AI') = (ai_status = 'SUCCEEDED')
        AND (classification_source <> 'AI' OR (needs_review AND ai_model IS NOT NULL))
        AND ((ai_status = 'FAILED') = (ai_failure IS NOT NULL))
        AND ((ai_status = 'NOT_REQUESTED') = (ai_attempted_at IS NULL))
    );
