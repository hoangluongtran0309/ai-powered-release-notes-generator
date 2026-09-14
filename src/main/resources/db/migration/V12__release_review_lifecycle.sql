-- Releases pass through review and approval before publication (ADR-0010).
-- A Project may prepare several releases at once; each change is still in at most one.
DROP INDEX releases_one_draft_per_project;

-- Releases published before this migration were never approved, and published rows
-- cannot change, so a PUBLISHED release may lack an approval.
ALTER TABLE releases
    DROP CONSTRAINT releases_status_known,
    ADD CONSTRAINT releases_status_known CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED')),
    ADD COLUMN planned_release_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN approved_by UUID,
    ADD COLUMN approver_name VARCHAR(120),
    ADD CONSTRAINT releases_approver_fk
        FOREIGN KEY (approved_by, organization_id)
        REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT releases_approval_recorded CHECK (
        (approved_at IS NULL) = (approved_by IS NULL)
        AND (approved_by IS NULL) = (approver_name IS NULL)
        AND (status <> 'APPROVED' OR approved_at IS NOT NULL)
        AND (status NOT IN ('DRAFT', 'IN_REVIEW') OR approved_at IS NULL)
    );

ALTER TABLE release_changes
    ADD CONSTRAINT release_changes_tenant_unique UNIQUE (release_id, change_id, organization_id, project_id);

-- One decision per change of a release under review. The reviewer is also recorded on
-- the change itself; this row records that the release's review covered the change.
CREATE TABLE release_change_reviews (
    release_id UUID NOT NULL,
    change_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    action VARCHAR(10) NOT NULL,
    reviewer_id UUID NOT NULL,
    reviewer_name VARCHAR(120) NOT NULL,
    note TEXT,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_change_reviews_pk PRIMARY KEY (release_id, change_id),
    CONSTRAINT release_change_reviews_item_fk
        FOREIGN KEY (release_id, change_id, organization_id, project_id)
        REFERENCES release_changes (release_id, change_id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT release_change_reviews_reviewer_fk
        FOREIGN KEY (reviewer_id, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT release_change_reviews_action_known CHECK (action IN ('APPROVE', 'EDIT')),
    CONSTRAINT release_change_reviews_note_valid CHECK (
        note IS NULL OR (BTRIM(note) <> '' AND char_length(note) <= 2000)
    )
);

-- The changes of a release are chosen while it is a draft; rejecting one during review
-- removes it. Deleting a release cascades after its row is gone, so each check only
-- applies while the release still exists.
DROP TRIGGER release_changes_published_immutable ON release_changes;
DROP FUNCTION reject_published_release_change_mutation();

CREATE FUNCTION enforce_release_change_membership() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'The changes of a release cannot be modified (release %).', OLD.release_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF EXISTS (SELECT 1 FROM releases WHERE id = NEW.release_id AND status <> 'DRAFT') THEN
            RAISE EXCEPTION 'Changes can only be added to a draft release (release %).', NEW.release_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM releases WHERE id = OLD.release_id AND status IN ('APPROVED', 'PUBLISHED')) THEN
        RAISE EXCEPTION 'The changes of an approved or published release are fixed (release %).', OLD.release_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER release_changes_follow_release_status
    BEFORE INSERT OR UPDATE OR DELETE ON release_changes
    FOR EACH ROW EXECUTE FUNCTION enforce_release_change_membership();

-- Decisions are recorded while a release is in review and are fixed once it is approved.
CREATE FUNCTION enforce_release_change_review() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        IF NOT EXISTS (SELECT 1 FROM releases WHERE id = NEW.release_id AND status = 'IN_REVIEW') THEN
            RAISE EXCEPTION 'Review decisions can only be recorded while a release is in review (release %).',
                NEW.release_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF EXISTS (SELECT 1 FROM releases WHERE id = OLD.release_id AND status IN ('APPROVED', 'PUBLISHED')) THEN
        RAISE EXCEPTION 'The review decisions of an approved or published release are fixed (release %).',
            OLD.release_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER release_change_reviews_follow_release_status
    BEFORE INSERT OR UPDATE OR DELETE ON release_change_reviews
    FOR EACH ROW EXECUTE FUNCTION enforce_release_change_review();
