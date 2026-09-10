CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT projects_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT projects_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE INDEX projects_organization_created_idx
    ON projects (organization_id, created_at, id);

CREATE TABLE github_integrations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    repository_owner VARCHAR(39) NOT NULL,
    repository_name VARCHAR(100) NOT NULL,
    webhook_id UUID NOT NULL,
    secret_nonce BYTEA NOT NULL,
    secret_ciphertext BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT github_integrations_project_organization_fk
        FOREIGN KEY (project_id, organization_id)
        REFERENCES projects (id, organization_id),
    CONSTRAINT github_integrations_project_unique UNIQUE (project_id),
    CONSTRAINT github_integrations_repository_unique
        UNIQUE (organization_id, repository_owner, repository_name),
    CONSTRAINT github_integrations_webhook_id_unique UNIQUE (webhook_id),
    CONSTRAINT github_integrations_owner_canonical CHECK (
        repository_owner = LOWER(BTRIM(repository_owner))
        AND repository_owner ~ '^[a-z0-9_.-]+$'
    ),
    CONSTRAINT github_integrations_repository_canonical CHECK (
        repository_name = LOWER(BTRIM(repository_name))
        AND repository_name ~ '^[a-z0-9_.-]+$'
    ),
    CONSTRAINT github_integrations_nonce_length CHECK (OCTET_LENGTH(secret_nonce) = 12),
    CONSTRAINT github_integrations_ciphertext_length CHECK (OCTET_LENGTH(secret_ciphertext) > 16)
);
