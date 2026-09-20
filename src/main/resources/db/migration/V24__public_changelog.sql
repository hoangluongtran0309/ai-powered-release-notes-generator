-- A public changelog (ADR-0022): the one place a release note goes that ReleaseFlow
-- serves itself. An Organization gets a slug that names it in a URL, and a
-- PUBLIC_CHANGELOG Action copies a published note into a permanent public entry.

-- The slug is one DNS label, so the same value works as a path segment today and as a
-- subdomain wherever a deployment provisions wildcard DNS.
ALTER TABLE organizations
    ADD COLUMN slug VARCHAR(63);

-- Every Organization that exists already gets a slug made from its name: lowercase,
-- anything that is not a letter or a digit becomes a hyphen, runs of hyphens collapse,
-- and the ends are trimmed. A name with nothing usable in it falls back to "org".
-- Collisions are numbered by age, so the oldest Organization keeps the plain slug.
WITH derived AS (
    SELECT
        id,
        LEFT(
            COALESCE(
                NULLIF(BTRIM(REGEXP_REPLACE(LOWER(name), '[^a-z0-9]+', '-', 'g'), '-'), ''),
                'org'
            ),
            63
        ) AS base,
        created_at
    FROM organizations
),
numbered AS (
    SELECT
        id,
        base,
        ROW_NUMBER() OVER (PARTITION BY base ORDER BY created_at, id) AS position
    FROM derived
)
UPDATE organizations
SET slug = CASE
    WHEN numbered.position = 1 THEN numbered.base
    -- Leave room for the suffix rather than overrun the 63-character label limit.
    ELSE LEFT(numbered.base, 63 - LENGTH('-' || numbered.position)) || '-' || numbered.position
END
FROM numbered
WHERE organizations.id = numbered.id;

ALTER TABLE organizations
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT organizations_slug_unique UNIQUE (slug),
    ADD CONSTRAINT organizations_slug_valid CHECK (slug ~ '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$');

-- An Action run is now pointed at from outside its own capability, so it gains the
-- composite key every other tenant-owned table already has.
ALTER TABLE automation_action_runs
    ADD CONSTRAINT automation_action_runs_id_organization_unique UNIQUE (id, organization_id);

-- One published note, made public. Everything a reader sees is snapshotted here, so
-- renaming the Organization, the Project, or the audience never rewrites what was
-- published, and the entry outlives the Rule that made it.
CREATE TABLE public_changelog_entries (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NOT NULL,
    release_id UUID NOT NULL,
    action_run_id UUID NOT NULL,
    organization_slug_snapshot VARCHAR(63) NOT NULL,
    organization_name_snapshot VARCHAR(120) NOT NULL,
    project_name_snapshot VARCHAR(120) NOT NULL,
    release_version_snapshot VARCHAR(50) NOT NULL,
    audience_name_snapshot VARCHAR(120) NOT NULL,
    language VARCHAR(16) NOT NULL,
    content_snapshot TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT public_changelog_entries_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id),
    CONSTRAINT public_changelog_entries_action_run_fk
        FOREIGN KEY (action_run_id, organization_id)
        REFERENCES automation_action_runs (id, organization_id),
    -- One entry per Release, audience, and language: a repeated delivery of the same
    -- note is the entry that is already there.
    CONSTRAINT public_changelog_entries_publication_unique
        UNIQUE (organization_id, release_id, audience_name_snapshot, language),
    -- And one entry per Action run, so a retry cannot quietly publish a second copy.
    CONSTRAINT public_changelog_entries_action_run_unique UNIQUE (action_run_id),
    CONSTRAINT public_changelog_entries_content_not_blank CHECK (BTRIM(content_snapshot) <> ''),
    CONSTRAINT public_changelog_entries_slug_valid
        CHECK (organization_slug_snapshot ~ '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$')
);

CREATE INDEX public_changelog_entries_feed_idx
    ON public_changelog_entries (organization_id, published_at DESC, id);

-- Published material is never edited or withdrawn: the page a reader bookmarked says
-- what it said. Even direct SQL is refused.
CREATE FUNCTION reject_public_changelog_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Public changelog entries are immutable (entry %).', OLD.id
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER public_changelog_entries_immutable
    BEFORE UPDATE OR DELETE ON public_changelog_entries
    FOR EACH ROW EXECUTE FUNCTION reject_public_changelog_mutation();

-- A fourth kind of Action.
ALTER TABLE automation_rule_actions
    DROP CONSTRAINT automation_rule_actions_type_known,
    ADD CONSTRAINT automation_rule_actions_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG'));

ALTER TABLE automation_action_runs
    DROP CONSTRAINT automation_action_runs_type_known,
    ADD CONSTRAINT automation_action_runs_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG'));
