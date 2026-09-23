CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT organizations_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT app_users_email_unique UNIQUE (email),
    CONSTRAINT app_users_email_canonical CHECK (email = LOWER(BTRIM(email))),
    CONSTRAINT app_users_display_name_not_blank CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT app_users_role_valid CHECK (role IN ('OWNER'))
);

CREATE INDEX app_users_organization_id_idx ON app_users (organization_id);
