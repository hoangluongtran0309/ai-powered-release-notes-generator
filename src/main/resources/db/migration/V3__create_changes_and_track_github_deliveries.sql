ALTER TABLE github_integrations
    ADD COLUMN last_delivery_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE changes (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    pull_request_number INTEGER NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    author_login VARCHAR(100) NOT NULL,
    labels TEXT[] NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    merge_commit_sha VARCHAR(64) NOT NULL,
    merged_at TIMESTAMP WITH TIME ZONE NOT NULL,
    url VARCHAR(2048) NOT NULL,
    delivery_id UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT changes_project_organization_fk
        FOREIGN KEY (project_id, organization_id)
        REFERENCES projects (id, organization_id),
    CONSTRAINT changes_project_pull_request_unique UNIQUE (project_id, pull_request_number),
    CONSTRAINT changes_pull_request_number_positive CHECK (pull_request_number > 0),
    CONSTRAINT changes_title_not_blank CHECK (BTRIM(title) <> ''),
    CONSTRAINT changes_merge_commit_sha_format
        CHECK (merge_commit_sha ~ '^([0-9a-f]{40}|[0-9a-f]{64})$')
);
