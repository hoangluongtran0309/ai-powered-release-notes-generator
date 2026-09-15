-- Categories become a catalog per Organization (ADR-0012). Breaking stays a flag.

CREATE TABLE category_definitions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    category_group VARCHAR(20) NOT NULL,
    system_category BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT category_definitions_code_unique UNIQUE (organization_id, code),
    CONSTRAINT category_definitions_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT category_definitions_code_valid CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT category_definitions_display_name_not_blank CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT category_definitions_group_known CHECK (
        category_group IN ('FEATURE', 'FIX', 'PERFORMANCE', 'DOCUMENTATION', 'MAINTENANCE', 'OTHER')
    ),
    CONSTRAINT category_definitions_system_active CHECK (NOT system_category OR active),
    CONSTRAINT category_definitions_unknown_is_system CHECK (
        (code = 'UNKNOWN') = system_category AND (code <> 'UNKNOWN' OR category_group = 'OTHER')
    )
);

-- Every Organization starts with the categories that used to be fixed. CategoryService
-- seeds the same rows at registration.
INSERT INTO category_definitions (
    id, organization_id, code, display_name, category_group, system_category, active, created_at, updated_at
)
SELECT gen_random_uuid(), o.id, d.code, d.display_name, d.category_group, d.code = 'UNKNOWN', TRUE, now(), now()
FROM organizations o
CROSS JOIN (
    VALUES
        ('FEATURE', 'Feature', 'FEATURE'),
        ('FIX', 'Fix', 'FIX'),
        ('PERFORMANCE', 'Performance', 'PERFORMANCE'),
        ('DOCUMENTATION', 'Documentation', 'DOCUMENTATION'),
        ('MAINTENANCE', 'Maintenance', 'MAINTENANCE'),
        ('UNKNOWN', 'Unknown', 'OTHER')
) AS d(code, display_name, category_group);

-- A change keeps a snapshot of its category, so renaming or archiving a category never
-- rewrites history. The former fixed values map one to one.
ALTER TABLE changes
    DROP CONSTRAINT changes_category_known,
    ALTER COLUMN category TYPE VARCHAR(64),
    ADD COLUMN category_display_name VARCHAR(120),
    ADD COLUMN category_group VARCHAR(20);

UPDATE changes
SET category_display_name = CASE category
        WHEN 'FEATURE' THEN 'Feature'
        WHEN 'FIX' THEN 'Fix'
        WHEN 'PERFORMANCE' THEN 'Performance'
        WHEN 'DOCUMENTATION' THEN 'Documentation'
        WHEN 'MAINTENANCE' THEN 'Maintenance'
        ELSE 'Unknown'
    END,
    category_group = CASE category WHEN 'UNKNOWN' THEN 'OTHER' ELSE category END;

-- A category the AI proposed and an administrator approved is recorded as SUGGESTION.
ALTER TABLE changes
    ALTER COLUMN category_display_name SET NOT NULL,
    ALTER COLUMN category_group SET NOT NULL,
    ADD CONSTRAINT changes_category_valid CHECK (
        category ~ '^[A-Z][A-Z0-9_]*$'
        AND BTRIM(category_display_name) <> ''
        AND category_group IN ('FEATURE', 'FIX', 'PERFORMANCE', 'DOCUMENTATION', 'MAINTENANCE', 'OTHER')
        AND (category <> 'UNKNOWN' OR category_group = 'OTHER')
    ),
    DROP CONSTRAINT changes_classification_source_known,
    ADD CONSTRAINT changes_classification_source_known
        CHECK (classification_source IN ('RULES', 'AI', 'SUGGESTION', 'HUMAN')),
    DROP CONSTRAINT changes_ai_state_consistent,
    ADD CONSTRAINT changes_ai_state_consistent CHECK (
        (classification_source NOT IN ('AI', 'SUGGESTION') OR ai_status = 'SUCCEEDED')
        AND (ai_status <> 'SUCCEEDED' OR ai_model IS NOT NULL)
        AND ((ai_status = 'FAILED') = (ai_failure IS NOT NULL))
        AND ((ai_status = 'NOT_REQUESTED') = (ai_attempted_at IS NULL))
    );

-- One proposal per change, decided once by an administrator. It never activates a
-- category by itself.
CREATE TABLE category_suggestions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    change_id UUID NOT NULL,
    proposed_code VARCHAR(64) NOT NULL,
    proposed_name VARCHAR(120) NOT NULL,
    proposed_group VARCHAR(20) NOT NULL,
    rationale VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    resolved_code VARCHAR(64),
    decided_by UUID,
    decider_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT category_suggestions_change_fk
        FOREIGN KEY (change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT category_suggestions_decider_fk
        FOREIGN KEY (decided_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT category_suggestions_change_unique UNIQUE (change_id),
    CONSTRAINT category_suggestions_code_valid CHECK (proposed_code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT category_suggestions_name_not_blank CHECK (BTRIM(proposed_name) <> ''),
    CONSTRAINT category_suggestions_group_known CHECK (
        proposed_group IN ('FEATURE', 'FIX', 'PERFORMANCE', 'DOCUMENTATION', 'MAINTENANCE', 'OTHER')
    ),
    CONSTRAINT category_suggestions_status_known CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'MAPPED', 'REJECTED')),
    CONSTRAINT category_suggestions_decision_recorded CHECK (
        (status = 'PENDING_REVIEW') = (decided_at IS NULL)
        AND (decided_at IS NULL) = (decided_by IS NULL)
        AND (decided_by IS NULL) = (decider_name IS NULL)
        AND (resolved_code IS NOT NULL) = (status IN ('APPROVED', 'MAPPED'))
    )
);

CREATE INDEX category_suggestions_status_idx ON category_suggestions (organization_id, status, created_at);
