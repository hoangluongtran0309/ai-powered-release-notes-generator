-- Two more kinds of Action: a child page in Notion and a page in a Confluence Cloud
-- space. Both are ordinary deliveries, so they need nothing but room in the two lists
-- of Action kinds: what each one is configured with lives in the configuration object
-- the Action already carries, and its credential in the secret columns already there.

ALTER TABLE automation_rule_actions
    DROP CONSTRAINT automation_rule_actions_type_known,
    ADD CONSTRAINT automation_rule_actions_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG', 'NOTION', 'CONFLUENCE'));

ALTER TABLE automation_action_runs
    DROP CONSTRAINT automation_action_runs_type_known,
    ADD CONSTRAINT automation_action_runs_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG', 'NOTION', 'CONFLUENCE'));
