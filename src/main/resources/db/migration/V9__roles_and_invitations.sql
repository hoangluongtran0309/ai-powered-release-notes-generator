ALTER TABLE app_users DROP CONSTRAINT app_users_role_valid;
UPDATE app_users SET role = 'ADMIN' WHERE role = 'OWNER';
ALTER TABLE app_users ADD CONSTRAINT app_users_role_valid CHECK (role IN ('ADMIN', 'MEMBER'));

-- Only a SHA-256 hash of each invitation token is stored; the raw token is shown once.
CREATE TABLE organization_invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    accepted_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT organization_invitations_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT organization_invitations_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT organization_invitations_email_canonical CHECK (email = LOWER(BTRIM(email)) AND email <> ''),
    CONSTRAINT organization_invitations_status_known
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT organization_invitations_creator_fk
        FOREIGN KEY (created_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT organization_invitations_acceptor_fk
        FOREIGN KEY (accepted_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT organization_invitations_state_consistent CHECK (
        (status = 'ACCEPTED') = (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
        AND (status = 'REVOKED') = (revoked_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX organization_invitations_one_pending
    ON organization_invitations (organization_id, email) WHERE status = 'PENDING';
