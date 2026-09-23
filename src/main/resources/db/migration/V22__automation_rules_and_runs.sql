-- Automation delivers a published release note where its readers already are
-- (ADR-0020): an Organization writes Rules, each Rule holds an ordered list of
-- Actions, and every firing is a durable Run that a worker walks one Action at a
-- time. Publication itself only records an outbox row, so a Rule that cannot be
-- delivered never rolls the publication back.

-- A Rule belongs to the Organization and, optionally, to one of its Projects; a
-- Rule without a Project watches all of them. A Rule starts disabled: enabling it
-- is what checks that every Action can actually be carried out.
CREATE TABLE automation_rules (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID,
    name VARCHAR(120) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT automation_rules_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT automation_rules_project_fk
        FOREIGN KEY (project_id, organization_id) REFERENCES projects (id, organization_id),
    CONSTRAINT automation_rules_trigger_known CHECK (trigger_type IN ('RELEASE_PUBLISHED', 'MANUAL')),
    CONSTRAINT automation_rules_name_not_blank CHECK (BTRIM(name) <> '' AND name = BTRIM(name)),
    -- An archived Rule is never enabled again; it only stays for the Runs it made.
    CONSTRAINT automation_rules_archived_not_enabled CHECK (active OR NOT enabled)
);

-- Two Rules of one Organization cannot share a name, whatever the casing, but an
-- archived one frees its name.
CREATE UNIQUE INDEX automation_rules_name_unique
    ON automation_rules (organization_id, LOWER(name))
    WHERE active;

CREATE INDEX automation_rules_match_idx
    ON automation_rules (organization_id, trigger_type)
    WHERE enabled AND active;

-- One step of a Rule, at a fixed position. The audience and language choose which
-- of a release's notes is delivered; the configuration and secret say where.
CREATE TABLE automation_rule_actions (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    position INTEGER NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    audience_id UUID NOT NULL,
    target_language VARCHAR(16) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    secret_nonce BYTEA,
    secret_ciphertext BYTEA,
    CONSTRAINT automation_rule_actions_rule_fk
        FOREIGN KEY (rule_id, organization_id)
        REFERENCES automation_rules (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT automation_rule_actions_audience_fk
        FOREIGN KEY (audience_id, organization_id)
        REFERENCES audience_definitions (id, organization_id),
    -- Deferred, so reordering a rule's actions inside one transaction never collides
    -- with itself half-way through.
    CONSTRAINT automation_rule_actions_position_unique UNIQUE (rule_id, position)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT automation_rule_actions_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT automation_rule_actions_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL')),
    CONSTRAINT automation_rule_actions_position_non_negative CHECK (position >= 0),
    CONSTRAINT automation_rule_actions_configuration_object CHECK (jsonb_typeof(configuration) = 'object'),
    -- A secret is stored encrypted or not at all; half of one is never written.
    CONSTRAINT automation_rule_actions_secret_paired
        CHECK ((secret_nonce IS NULL) = (secret_ciphertext IS NULL))
);

CREATE INDEX automation_rule_actions_rule_idx ON automation_rule_actions (rule_id);

-- One firing of one Rule against one Release. Everything a reader of the history
-- needs is snapshotted, so renaming or archiving the Rule never rewrites the past.
CREATE TABLE automation_runs (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NOT NULL,
    release_id UUID NOT NULL,
    rule_name_snapshot VARCHAR(120) NOT NULL,
    release_version_snapshot VARCHAR(50) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    request_id UUID,
    initiated_by UUID,
    initiator_name VARCHAR(120),
    status VARCHAR(20) NOT NULL,
    cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT automation_runs_id_organization_unique UNIQUE (id, organization_id),
    CONSTRAINT automation_runs_rule_fk
        FOREIGN KEY (rule_id, organization_id) REFERENCES automation_rules (id, organization_id),
    CONSTRAINT automation_runs_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id),
    CONSTRAINT automation_runs_initiator_fk
        FOREIGN KEY (initiated_by, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT automation_runs_trigger_known CHECK (trigger_type IN ('RELEASE_PUBLISHED', 'MANUAL')),
    CONSTRAINT automation_runs_status_known
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED')),
    -- A person asks for a manual Run and is recorded with it; a publication does not.
    CONSTRAINT automation_runs_requester_matches_trigger CHECK (
        (trigger_type = 'MANUAL') = (request_id IS NOT NULL)
        AND (initiated_by IS NULL) = (initiator_name IS NULL)
    ),
    CONSTRAINT automation_runs_completion_consistent CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED')) = (completed_at IS NOT NULL)
    )
);

-- A publication fires each matching Rule once, and a repeated manual request is the
-- same Run rather than a second delivery.
CREATE UNIQUE INDEX automation_runs_published_once
    ON automation_runs (rule_id, release_id)
    WHERE trigger_type = 'RELEASE_PUBLISHED';
CREATE UNIQUE INDEX automation_runs_request_once
    ON automation_runs (rule_id, request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX automation_runs_recent_idx ON automation_runs (organization_id, created_at DESC, id DESC);
CREATE INDEX automation_runs_rule_idx ON automation_runs (rule_id, status);

-- One Action of one Run, with everything the delivery needs frozen at creation. The
-- note content is null when the Release had none for that audience and language: the
-- Action then fails, which is the whole point of keeping publication out of it.
CREATE TABLE automation_action_runs (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    rule_action_id UUID NOT NULL,
    position INTEGER NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    audience_id UUID NOT NULL,
    audience_name_snapshot VARCHAR(120) NOT NULL,
    language_snapshot VARCHAR(16) NOT NULL,
    note_content_snapshot TEXT,
    configuration_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    secret_nonce_snapshot BYTEA,
    secret_ciphertext_snapshot BYTEA,
    status VARCHAR(20) NOT NULL,
    external_reference VARCHAR(1024),
    error_code VARCHAR(100),
    attempts INTEGER NOT NULL DEFAULT 0,
    claimed_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT automation_action_runs_run_fk
        FOREIGN KEY (run_id, organization_id)
        REFERENCES automation_runs (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT automation_action_runs_audience_fk
        FOREIGN KEY (audience_id, organization_id)
        REFERENCES audience_definitions (id, organization_id),
    CONSTRAINT automation_action_runs_position_unique UNIQUE (run_id, position),
    CONSTRAINT automation_action_runs_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL')),
    CONSTRAINT automation_action_runs_status_known
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED')),
    CONSTRAINT automation_action_runs_position_non_negative CHECK (position >= 0),
    CONSTRAINT automation_action_runs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT automation_action_runs_configuration_object
        CHECK (jsonb_typeof(configuration_snapshot) = 'object'),
    CONSTRAINT automation_action_runs_secret_paired
        CHECK ((secret_nonce_snapshot IS NULL) = (secret_ciphertext_snapshot IS NULL)),
    -- Only a failed Action explains itself, and only a finished one has an end.
    CONSTRAINT automation_action_runs_error_when_failed
        CHECK ((status IN ('FAILED', 'UNKNOWN')) = (error_code IS NOT NULL)),
    CONSTRAINT automation_action_runs_completion_consistent CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED')) = (completed_at IS NOT NULL)
    )
);

CREATE INDEX automation_action_runs_claim_idx ON automation_action_runs (status, claimed_at);
CREATE INDEX automation_action_runs_run_idx ON automation_action_runs (run_id, position);

-- The outbox. Publishing a Release writes one row here inside its own transaction
-- and nothing else, so nothing automation does can undo the publication. The worker
-- turns the row into Runs once that transaction has committed.
CREATE TABLE automation_publish_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NOT NULL,
    release_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT automation_publish_jobs_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id),
    -- A Release is published once, so it is announced to automation once.
    CONSTRAINT automation_publish_jobs_release_unique UNIQUE (release_id),
    CONSTRAINT automation_publish_jobs_status_known
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT automation_publish_jobs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT automation_publish_jobs_completion_consistent CHECK (
        (status IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL)
    )
);

CREATE INDEX automation_publish_jobs_due_idx ON automation_publish_jobs (status, next_attempt_at);
