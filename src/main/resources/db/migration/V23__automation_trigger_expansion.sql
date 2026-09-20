-- Three more ways a Rule fires (ADR-0021): a cron schedule in a civil time zone, a
-- reminder a chosen number of days before a planned release, and a signed call from
-- another system. All three end in the same durable Run of ADR-0020; only the way the
-- firing is decided is new, and every one of them is recorded before it is carried out.

ALTER TABLE automation_rules
    -- The Release a cron Rule repeats. Its Project is the Release's, never a separate
    -- choice, so the composite key below can tie the three together.
    ADD COLUMN trigger_release_id UUID,
    ADD COLUMN cron_expression VARCHAR(255),
    ADD COLUMN cron_time_zone VARCHAR(64),
    ADD COLUMN reminder_days_before INTEGER,
    -- The public identity of an inbound webhook, and the secret that proves a call.
    ADD COLUMN webhook_id UUID,
    ADD COLUMN webhook_secret_nonce BYTEA,
    ADD COLUMN webhook_secret_ciphertext BYTEA,
    ADD COLUMN next_fire_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE automation_rules
    DROP CONSTRAINT automation_rules_trigger_known,
    ADD CONSTRAINT automation_rules_trigger_known CHECK (trigger_type IN (
        'RELEASE_PUBLISHED',
        'MANUAL',
        'SCHEDULED_CRON',
        'UPCOMING_RELEASE_REMINDER',
        'EXTERNAL_WEBHOOK'
    )),
    ADD CONSTRAINT automation_rules_trigger_release_fk
        FOREIGN KEY (trigger_release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id),
    -- A cron Rule is nothing without its Release, its expression, and its zone; no
    -- other trigger carries them.
    ADD CONSTRAINT automation_rules_cron_configuration CHECK (
        (trigger_type = 'SCHEDULED_CRON') = (
            trigger_release_id IS NOT NULL
            AND project_id IS NOT NULL
            AND cron_expression IS NOT NULL
            AND cron_time_zone IS NOT NULL
        )
    ),
    ADD CONSTRAINT automation_rules_reminder_configuration CHECK (
        (trigger_type = 'UPCOMING_RELEASE_REMINDER') = (reminder_days_before IS NOT NULL)
    ),
    ADD CONSTRAINT automation_rules_reminder_days_in_range CHECK (
        reminder_days_before IS NULL OR reminder_days_before BETWEEN 0 AND 365
    ),
    ADD CONSTRAINT automation_rules_webhook_configuration CHECK (
        (trigger_type = 'EXTERNAL_WEBHOOK') = (webhook_id IS NOT NULL)
    ),
    -- A secret is stored encrypted or not at all, exactly as an Action's is.
    ADD CONSTRAINT automation_rules_webhook_secret_paired CHECK (
        (webhook_secret_nonce IS NULL) = (webhook_secret_ciphertext IS NULL)
        AND (webhook_id IS NULL) = (webhook_secret_nonce IS NULL)
    ),
    -- Only a cron Rule has a next firing, and a Rule that is off never has one, so a
    -- disabled or archived Rule can never be claimed.
    ADD CONSTRAINT automation_rules_next_fire_only_when_running CHECK (
        next_fire_at IS NULL OR (trigger_type = 'SCHEDULED_CRON' AND enabled AND active)
    );

-- One webhook path belongs to one Rule, whatever the Organization.
CREATE UNIQUE INDEX automation_rules_webhook_id_unique
    ON automation_rules (webhook_id)
    WHERE webhook_id IS NOT NULL;

CREATE INDEX automation_rules_due_cron_idx
    ON automation_rules (next_fire_at, id)
    WHERE trigger_type = 'SCHEDULED_CRON' AND enabled AND active;

CREATE INDEX automation_rules_reminder_scan_idx
    ON automation_rules (organization_id, project_id)
    WHERE trigger_type = 'UPCOMING_RELEASE_REMINDER' AND enabled AND active;

-- The occurrence a scheduled Run answers: the cron instant, or the moment the reminder
-- was due. It is what makes a repeated scan the same Run rather than another delivery.
ALTER TABLE automation_runs
    ADD COLUMN scheduled_for TIMESTAMP WITH TIME ZONE;

ALTER TABLE automation_runs
    DROP CONSTRAINT automation_runs_trigger_known,
    ADD CONSTRAINT automation_runs_trigger_known CHECK (trigger_type IN (
        'RELEASE_PUBLISHED',
        'MANUAL',
        'SCHEDULED_CRON',
        'UPCOMING_RELEASE_REMINDER',
        'EXTERNAL_WEBHOOK'
    )),
    DROP CONSTRAINT automation_runs_requester_matches_trigger,
    -- A person asks for a manual Run and is recorded with it; a call from outside
    -- brings its own delivery ID instead, and nobody is recorded for either a
    -- publication or a schedule.
    ADD CONSTRAINT automation_runs_requester_matches_trigger CHECK (
        (trigger_type IN ('MANUAL', 'EXTERNAL_WEBHOOK')) = (request_id IS NOT NULL)
        AND (initiated_by IS NULL) = (initiator_name IS NULL)
        AND (initiated_by IS NULL OR trigger_type = 'MANUAL')
    ),
    ADD CONSTRAINT automation_runs_schedule_matches_trigger CHECK (
        (trigger_type IN ('SCHEDULED_CRON', 'UPCOMING_RELEASE_REMINDER')) = (scheduled_for IS NOT NULL)
    );

-- A cron occurrence fires its Rule once, however often the worker looks, and a reminder
-- is one per Rule, Release, and due moment: rescheduling a Release moves that moment and
-- so earns exactly one new reminder.
CREATE UNIQUE INDEX automation_runs_cron_once
    ON automation_runs (rule_id, scheduled_for)
    WHERE trigger_type = 'SCHEDULED_CRON';
CREATE UNIQUE INDEX automation_runs_reminder_once
    ON automation_runs (rule_id, release_id, scheduled_for)
    WHERE trigger_type = 'UPCOMING_RELEASE_REMINDER';
