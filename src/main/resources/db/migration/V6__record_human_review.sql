ALTER TABLE app_users
    ADD CONSTRAINT app_users_id_organization_unique UNIQUE (id, organization_id);

ALTER TABLE changes
    ADD COLUMN reviewed_by UUID,
    ADD COLUMN reviewer_name VARCHAR(120),
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT changes_reviewer_fk
        FOREIGN KEY (reviewed_by, organization_id)
        REFERENCES app_users (id, organization_id);

-- A person may settle any classification, including clearing a breaking flag, but
-- breaking, Unknown, and AI-suggested changes leave review only through a recorded review.
ALTER TABLE changes
    DROP CONSTRAINT changes_classification_source_known,
    ADD CONSTRAINT changes_classification_source_known
        CHECK (classification_source IN ('RULES', 'AI', 'HUMAN')),
    DROP CONSTRAINT changes_review_required,
    ADD CONSTRAINT changes_review_required CHECK (
        needs_review OR reviewed_at IS NOT NULL OR (NOT breaking AND category <> 'UNKNOWN')
    ),
    DROP CONSTRAINT changes_ai_state_consistent,
    ADD CONSTRAINT changes_ai_state_consistent CHECK (
        (classification_source <> 'AI'
            OR (ai_status = 'SUCCEEDED' AND ai_model IS NOT NULL AND (needs_review OR reviewed_at IS NOT NULL)))
        AND (ai_status <> 'SUCCEEDED' OR classification_source IN ('AI', 'HUMAN'))
        AND ((ai_status = 'FAILED') = (ai_failure IS NOT NULL))
        AND ((ai_status = 'NOT_REQUESTED') = (ai_attempted_at IS NULL))
    ),
    ADD CONSTRAINT changes_review_recorded CHECK (
        (reviewed_by IS NULL) = (reviewed_at IS NULL)
        AND (reviewed_by IS NULL) = (reviewer_name IS NULL)
        AND (classification_source <> 'HUMAN' OR reviewed_at IS NOT NULL)
        AND (reviewed_at IS NULL OR (NOT needs_review AND category <> 'UNKNOWN'))
    );
