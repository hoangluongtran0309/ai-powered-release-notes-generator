-- Context sufficiency and possible duplicates (ADR-0013). Both only ever add a need
-- for review; neither merges nor removes a change.

-- How much evidence the pull request gave, assessed only when the AI answered.
-- Changes recorded earlier stay unassessed.
ALTER TABLE changes
    ADD COLUMN context_score INTEGER,
    ADD COLUMN context_status VARCHAR(20),
    ADD COLUMN context_reasons JSONB,
    ADD CONSTRAINT changes_context_consistent CHECK (
        (context_score IS NULL) = (context_status IS NULL)
        AND (context_status IS NULL) = (context_reasons IS NULL)
        AND (context_score IS NULL OR (context_score BETWEEN 0 AND 100 AND ai_status = 'SUCCEEDED'))
        AND (context_status IS NULL OR context_status IN ('SUFFICIENT', 'INSUFFICIENT'))
        AND (context_reasons IS NULL OR jsonb_typeof(context_reasons) = 'array')
    );

-- A later change that looks like an earlier change of the same Project, with the
-- evidence and a person's one-time decision.
CREATE TABLE duplicate_candidates (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    change_id UUID NOT NULL,
    duplicate_of_id UUID NOT NULL,
    similarity DOUBLE PRECISION NOT NULL,
    evidence JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    decided_by UUID,
    decider_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT duplicate_candidates_change_fk
        FOREIGN KEY (change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT duplicate_candidates_duplicate_of_fk
        FOREIGN KEY (duplicate_of_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT duplicate_candidates_decider_fk
        FOREIGN KEY (decided_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT duplicate_candidates_pair_unique UNIQUE (change_id, duplicate_of_id),
    CONSTRAINT duplicate_candidates_distinct CHECK (change_id <> duplicate_of_id),
    CONSTRAINT duplicate_candidates_similarity_range CHECK (similarity BETWEEN 0 AND 1),
    CONSTRAINT duplicate_candidates_evidence_object CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT duplicate_candidates_status_known CHECK (status IN ('OPEN', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT duplicate_candidates_decision_recorded CHECK (
        (status = 'OPEN') = (decided_at IS NULL)
        AND (decided_at IS NULL) = (decided_by IS NULL)
        AND (decided_by IS NULL) = (decider_name IS NULL)
    )
);

CREATE INDEX duplicate_candidates_project_idx ON duplicate_candidates (organization_id, project_id, status);
CREATE INDEX duplicate_candidates_duplicate_of_idx ON duplicate_candidates (duplicate_of_id);
