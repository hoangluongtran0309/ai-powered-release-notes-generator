-- Release notes in several languages (ADR-0015): one note per audience and target
-- language, change content translated by a durable queue, and publication held until
-- every note is ready.

-- The languages an Organization's release notes are written in. No row means only its
-- output language.
CREATE TABLE organization_translation_settings (
    organization_id UUID PRIMARY KEY REFERENCES organizations (id),
    target_languages JSONB NOT NULL,
    updated_by UUID NOT NULL,
    updater_name VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT organization_translation_settings_updater_fk
        FOREIGN KEY (updated_by, organization_id)
        REFERENCES app_users (id, organization_id),
    -- CASE, because jsonb_array_length raises an error rather than failing on a non-array.
    CONSTRAINT organization_translation_settings_languages_bounded CHECK (
        CASE WHEN jsonb_typeof(target_languages) = 'array'
            THEN jsonb_array_length(target_languages) BETWEEN 1 AND 5
            ELSE false
        END
    )
);

-- An audience's template for one language. A language without a variant uses the
-- audience's main template.
CREATE TABLE audience_template_variants (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    audience_id UUID NOT NULL,
    language VARCHAR(16) NOT NULL,
    template_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audience_template_variants_audience_fk
        FOREIGN KEY (audience_id, organization_id)
        REFERENCES audience_definitions (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT audience_template_variants_language_unique UNIQUE (audience_id, language),
    CONSTRAINT audience_template_variants_template_valid CHECK (
        BTRIM(template_body) <> '' AND char_length(template_body) <= 10000
    )
);

-- One change's content translated into one language. The hash covers the source
-- language and texts, so an edited summary is translated again and an unchanged one
-- never is.
CREATE TABLE translation_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    change_id UUID NOT NULL,
    source_language VARCHAR(16) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    input JSONB NOT NULL,
    output JSONB,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT translation_jobs_change_fk
        FOREIGN KEY (change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT translation_jobs_input_unique UNIQUE (change_id, target_language, input_hash),
    CONSTRAINT translation_jobs_status_known CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT translation_jobs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT translation_jobs_input_object CHECK (jsonb_typeof(input) = 'object'),
    CONSTRAINT translation_jobs_output_when_succeeded CHECK (
        (status = 'SUCCEEDED') = (output IS NOT NULL)
        AND (output IS NULL OR jsonb_typeof(output) = 'object')
        AND (status IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL)
    )
);

CREATE INDEX translation_jobs_due_idx ON translation_jobs (status, next_attempt_at);

-- Translated texts, reused whenever the same text is translated between the same
-- languages again.
CREATE TABLE translation_cache (
    organization_id UUID NOT NULL REFERENCES organizations (id),
    source_language VARCHAR(16) NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    text_hash VARCHAR(64) NOT NULL,
    translated_text TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (organization_id, source_language, target_language, text_hash)
);

-- A note per audience and language; existing notes are ready.
ALTER TABLE release_audience_notes
    DROP CONSTRAINT release_audience_notes_audience_unique,
    ADD CONSTRAINT release_audience_notes_audience_language_unique UNIQUE (release_id, audience_id, language),
    ADD COLUMN translation_status VARCHAR(10) NOT NULL DEFAULT 'READY',
    ADD CONSTRAINT release_audience_notes_translation_status_known CHECK (
        translation_status IN ('READY', 'PENDING', 'FAILED')
    );

-- A release is published only when every one of its notes is ready.
CREATE FUNCTION require_ready_release_notes() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' AND EXISTS (
        SELECT 1 FROM release_audience_notes WHERE release_id = NEW.id AND translation_status <> 'READY'
    ) THEN
        RAISE EXCEPTION 'A release is published only when all its notes are ready (release %).', NEW.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER releases_publish_ready_notes
    BEFORE UPDATE ON releases
    FOR EACH ROW EXECUTE FUNCTION require_ready_release_notes();
