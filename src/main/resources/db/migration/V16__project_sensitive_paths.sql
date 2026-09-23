-- Sensitive-path patterns administrators add for one Project (ADR-0014). They extend the
-- deployment's baseline, which no Project can remove. No row means no additions.
CREATE TABLE project_sensitive_paths (
    project_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    additional_globs JSONB NOT NULL,
    updated_by UUID NOT NULL,
    updater_name VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT project_sensitive_paths_project_fk
        FOREIGN KEY (project_id, organization_id)
        REFERENCES projects (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT project_sensitive_paths_updater_fk
        FOREIGN KEY (updated_by, organization_id)
        REFERENCES app_users (id, organization_id),
    -- CASE, because jsonb_array_length raises an error rather than failing on a non-array.
    CONSTRAINT project_sensitive_paths_globs_bounded CHECK (
        CASE WHEN jsonb_typeof(additional_globs) = 'array'
            THEN jsonb_array_length(additional_globs) <= 100
            ELSE false
        END
    )
);
