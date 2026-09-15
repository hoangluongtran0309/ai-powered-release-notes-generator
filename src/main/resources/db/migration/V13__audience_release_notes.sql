-- Audiences and a release note for each of them (ADR-0011).

-- The kinds of reader an Organization writes release notes for. The code keys the
-- narratives stored on changes, so it never changes after creation.
CREATE TABLE audience_definitions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    communication_intent VARCHAR(1000) NOT NULL,
    template_body TEXT NOT NULL,
    preset BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audience_definitions_code_unique UNIQUE (organization_id, code),
    CONSTRAINT audience_definitions_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT audience_definitions_code_valid CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT audience_definitions_display_name_not_blank CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT audience_definitions_template_valid CHECK (
        BTRIM(template_body) <> '' AND char_length(template_body) <= 10000
    )
);

-- Every Organization starts with the three shipped audiences, named in its output
-- language (Vietnamese or English). AudiencePresets holds the same values.
WITH detailed(body) AS (
    VALUES ($body$- **{{whatChanged}}** ([#{{pullRequestNumber}}]({{pullRequestUrl}})){{#narrative}} — {{.}}{{/narrative}}
{{#whyChanged}}
  - %s: {{.}}
{{/whyChanged}}
{{#technicalDetail}}
  - %s: {{.}}
{{/technicalDetail}}
{{#migrationStep}}
  - %s: {{.}}
{{/migrationStep}}
$body$)
),
labels(language, operator_name, contributor_name, end_user_name, why, detail, implementation, migration) AS (
    VALUES
        ('en', 'Operator', 'Contributor', 'End user', 'Why', 'Detail', 'Implementation', 'Migration'),
        ('vi', 'Vận hành', 'Lập trình viên', 'Người dùng cuối', 'Lý do', 'Chi tiết', 'Cách triển khai', 'Cách chuyển đổi')
),
organization_labels AS (
    SELECT o.id AS organization_id, l.*
    FROM organizations o
    JOIN labels l ON l.language = CASE
        WHEN o.output_language = 'vi' OR o.output_language LIKE 'vi-%' THEN 'vi'
        ELSE 'en'
    END
)
INSERT INTO audience_definitions (
    id, organization_id, code, display_name, communication_intent, template_body, preset, created_at, updated_at
)
SELECT gen_random_uuid(), ol.organization_id, preset.code, preset.display_name, preset.intent, preset.body, TRUE, now(), now()
FROM organization_labels ol
CROSS JOIN detailed d
CROSS JOIN LATERAL (
    VALUES
        (
            'operator',
            ol.operator_name,
            'Focus on operational risk, rollback, and what to watch after deploy. Keep concrete numbers and failure modes.',
            format(d.body, ol.why, ol.detail, ol.migration)
        ),
        (
            'contributor',
            ol.contributor_name,
            'Focus on implementation detail and what other developers must change in their own code. Technical vocabulary is expected.',
            format(d.body, ol.why, ol.implementation, ol.migration)
        ),
        (
            'end_user',
            ol.end_user_name,
            'Plain language, no jargon and no technical metrics. Say only what the person will notice while using the product.',
            $plain$- **{{whatChanged}}**{{#narrative}} — {{.}}{{/narrative}}
$plain$
        )
) AS preset(code, display_name, intent, body);

-- Narratives are what the AI wrote for each audience, keyed by audience code. A person
-- may now write or correct a change's summary and narratives; the AI never overwrites them.
ALTER TABLE changes
    ADD COLUMN audience_narratives JSONB,
    ADD COLUMN summary_edited_by UUID,
    ADD COLUMN summary_editor_name VARCHAR(120),
    ADD COLUMN summary_edited_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT changes_summary_editor_fk
        FOREIGN KEY (summary_edited_by, organization_id)
        REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT changes_summary_edit_recorded CHECK (
        (summary_edited_by IS NULL) = (summary_editor_name IS NULL)
        AND (summary_editor_name IS NULL) = (summary_edited_at IS NULL)
    ),
    DROP CONSTRAINT changes_neutral_summary_consistent,
    ADD CONSTRAINT changes_neutral_summary_consistent CHECK (
        (neutral_summary IS NULL) = (content_language IS NULL)
        AND (
            neutral_summary IS NULL
            OR (jsonb_typeof(neutral_summary) = 'object' AND (ai_status = 'SUCCEEDED' OR summary_edited_at IS NOT NULL))
        )
        AND (summary_edited_at IS NULL OR neutral_summary IS NOT NULL)
        AND (audience_narratives IS NULL OR (jsonb_typeof(audience_narratives) = 'object' AND neutral_summary IS NOT NULL))
    );

-- One note per audience of an approved or published release. Notes are rendered at
-- approval from a snapshot of the audience's template; the published ones are the
-- immutable snapshot. The legacy release_notes table keeps the notes of releases
-- published before this migration and receives no new rows.
CREATE TABLE release_audience_notes (
    id UUID PRIMARY KEY,
    release_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    audience_id UUID NOT NULL,
    audience_code VARCHAR(64) NOT NULL,
    audience_name VARCHAR(120) NOT NULL,
    language VARCHAR(16) NOT NULL,
    template_body_snapshot TEXT NOT NULL,
    content TEXT NOT NULL,
    auto_rerender BOOLEAN NOT NULL,
    last_edited_by UUID,
    last_editor_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_audience_notes_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT release_audience_notes_audience_fk
        FOREIGN KEY (audience_id, organization_id)
        REFERENCES audience_definitions (id, organization_id),
    CONSTRAINT release_audience_notes_editor_fk
        FOREIGN KEY (last_edited_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT release_audience_notes_audience_unique UNIQUE (release_id, audience_id),
    CONSTRAINT release_audience_notes_content_not_blank CHECK (BTRIM(content) <> ''),
    CONSTRAINT release_audience_notes_edit_recorded CHECK (
        auto_rerender = (last_edited_by IS NULL)
        AND (last_edited_by IS NULL) = (last_editor_name IS NULL)
    )
);

CREATE INDEX release_audience_notes_audience_idx ON release_audience_notes (audience_id);

-- Notes are written only while their release is approved, and only their content and
-- edit record change. Once the release is published, nothing about them changes.
-- Deleting a release cascades after its row is gone, so that delete is allowed.
CREATE FUNCTION enforce_release_audience_note() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        IF NOT EXISTS (SELECT 1 FROM releases WHERE id = NEW.release_id AND status = 'APPROVED') THEN
            RAISE EXCEPTION 'Release notes can only be written while a release is approved (release %).',
                NEW.release_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        IF TG_OP = 'UPDATE' AND (
            NEW.id, NEW.release_id, NEW.organization_id, NEW.project_id, NEW.audience_id, NEW.audience_code,
            NEW.audience_name, NEW.language, NEW.template_body_snapshot, NEW.created_at
        ) IS DISTINCT FROM (
            OLD.id, OLD.release_id, OLD.organization_id, OLD.project_id, OLD.audience_id, OLD.audience_code,
            OLD.audience_name, OLD.language, OLD.template_body_snapshot, OLD.created_at
        ) THEN
            RAISE EXCEPTION 'Only the content of a release note can change (note %).', OLD.id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM releases WHERE id = OLD.release_id AND status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'The release notes of a published release are immutable (release %).', OLD.release_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER release_audience_notes_follow_release_status
    BEFORE INSERT OR UPDATE OR DELETE ON release_audience_notes
    FOR EACH ROW EXECUTE FUNCTION enforce_release_audience_note();

-- Notes are generated at approval. Releases approved before this migration have none,
-- so they return to review with their decisions kept, and approving them again
-- generates their notes. No public version has been released yet.
UPDATE releases
SET status = 'IN_REVIEW',
    approved_at = NULL,
    approved_by = NULL,
    approver_name = NULL,
    updated_at = now()
WHERE status = 'APPROVED';
