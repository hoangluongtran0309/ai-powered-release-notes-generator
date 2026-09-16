-- GitLab becomes the second kind of integration source (ADR-0017). A GitLab project is
-- named only by its path, lives on an instance the deployment allows, and proves its
-- deliveries in one of two ways. GitHub rows are untouched, so their ciphertexts, whose
-- authenticated data binds the repository owner and name, still decrypt.

ALTER TABLE integration_sources
    ALTER COLUMN repository_owner DROP NOT NULL,
    ALTER COLUMN repository_name DROP NOT NULL,
    ADD COLUMN api_base_url VARCHAR(255),
    ADD COLUMN webhook_auth_mode VARCHAR(30) NOT NULL DEFAULT 'GITHUB_HMAC';

ALTER TABLE integration_sources
    ALTER COLUMN webhook_auth_mode DROP DEFAULT,
    DROP CONSTRAINT integration_sources_type_known,
    ADD CONSTRAINT integration_sources_type_known CHECK (source_type IN ('GITHUB', 'GITLAB')),
    -- Only a GitHub source has an owner and a name; a GitLab project has a path.
    ADD CONSTRAINT integration_sources_github_repository CHECK (
        (source_type = 'GITHUB') = (repository_owner IS NOT NULL AND repository_name IS NOT NULL)
    ),
    ADD CONSTRAINT integration_sources_key_not_blank CHECK (BTRIM(external_project_key) <> ''),
    -- Only a source whose instance the Organization chose carries a base URL.
    ADD CONSTRAINT integration_sources_base_url_present CHECK (
        (source_type = 'GITLAB') = (api_base_url IS NOT NULL)
    ),
    ADD CONSTRAINT integration_sources_base_url_absolute CHECK (
        api_base_url IS NULL OR api_base_url ~ '^https?://[^/?#@]+(/[^?#]*)?$'
    ),
    ADD CONSTRAINT integration_sources_auth_mode_known CHECK (
        webhook_auth_mode IN ('GITHUB_HMAC', 'GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN')
    ),
    ADD CONSTRAINT integration_sources_auth_mode_matches_type CHECK (
        (source_type = 'GITHUB' AND webhook_auth_mode = 'GITHUB_HMAC')
        OR (source_type = 'GITLAB' AND webhook_auth_mode IN ('GITLAB_SIGNING_TOKEN', 'GITLAB_SECRET_TOKEN'))
    );

-- GitLab identifies a delivery only in its Standard Webhooks mode, and with a value that
-- is not always a GUID, so a webhook change may have no delivery ID. An imported change
-- still never has one.
ALTER TABLE changes
    DROP CONSTRAINT changes_delivery_matches_origin,
    ADD CONSTRAINT changes_import_has_no_delivery CHECK (origin <> 'IMPORT' OR delivery_id IS NULL);
