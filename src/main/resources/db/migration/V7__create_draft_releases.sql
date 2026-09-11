ALTER TABLE changes
    ADD CONSTRAINT changes_id_tenant_project_unique UNIQUE (id, organization_id, project_id);

CREATE TABLE releases (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    version VARCHAR(50) NOT NULL,
    summary TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT releases_project_organization_fk
        FOREIGN KEY (project_id, organization_id)
        REFERENCES projects (id, organization_id),
    CONSTRAINT releases_id_tenant_project_unique UNIQUE (id, organization_id, project_id),
    CONSTRAINT releases_status_known CHECK (status IN ('DRAFT')),
    CONSTRAINT releases_version_trimmed CHECK (BTRIM(version) <> '' AND version = BTRIM(version))
);

-- A Project prepares one release at a time.
CREATE UNIQUE INDEX releases_one_draft_per_project ON releases (project_id) WHERE status = 'DRAFT';

-- Both foreign keys carry the Organization and Project, so a change can only join a
-- release of its own Project, and each change belongs to at most one release.
CREATE TABLE release_changes (
    release_id UUID NOT NULL,
    change_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_changes_pk PRIMARY KEY (release_id, change_id),
    CONSTRAINT release_changes_change_unique UNIQUE (change_id),
    CONSTRAINT release_changes_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT release_changes_change_fk
        FOREIGN KEY (change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id)
);
