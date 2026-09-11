ALTER TABLE releases
    DROP CONSTRAINT releases_status_known,
    ADD CONSTRAINT releases_status_known CHECK (status IN ('DRAFT', 'PUBLISHED')),
    ADD COLUMN published_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN published_by UUID,
    ADD COLUMN publisher_name VARCHAR(120),
    ADD CONSTRAINT releases_publisher_fk
        FOREIGN KEY (published_by, organization_id)
        REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT releases_publication_recorded CHECK (
        (status = 'PUBLISHED') = (published_at IS NOT NULL)
        AND (published_at IS NULL) = (published_by IS NULL)
        AND (published_by IS NULL) = (publisher_name IS NULL)
    );

CREATE UNIQUE INDEX releases_project_version_unique ON releases (project_id, LOWER(version));

CREATE TABLE release_notes (
    release_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    version VARCHAR(50) NOT NULL,
    summary TEXT,
    sections JSONB NOT NULL,
    markdown TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_notes_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
);

-- Published Release Notes are immutable snapshots. These triggers enforce that for
-- every client, not only the application. Publishing itself (DRAFT -> PUBLISHED) is allowed.
CREATE FUNCTION reject_release_note_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Published release notes are immutable (release %).', OLD.release_id
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER release_notes_immutable
    BEFORE UPDATE OR DELETE ON release_notes
    FOR EACH ROW EXECUTE FUNCTION reject_release_note_mutation();

CREATE FUNCTION reject_published_release_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'Published releases are immutable (release %).', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER releases_published_immutable
    BEFORE UPDATE OR DELETE ON releases
    FOR EACH ROW EXECUTE FUNCTION reject_published_release_mutation();

CREATE FUNCTION reject_published_release_change_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    affected_release UUID;
BEGIN
    IF TG_OP = 'INSERT' THEN
        affected_release := NEW.release_id;
    ELSE
        affected_release := OLD.release_id;
    END IF;
    IF EXISTS (SELECT 1 FROM releases WHERE id = affected_release AND status = 'PUBLISHED')
        OR (TG_OP = 'UPDATE' AND EXISTS (
            SELECT 1 FROM releases WHERE id = NEW.release_id AND status = 'PUBLISHED'
        )) THEN
        RAISE EXCEPTION 'The changes of a published release are immutable (release %).', affected_release
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER release_changes_published_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON release_changes
    FOR EACH ROW EXECUTE FUNCTION reject_published_release_change_mutation();
