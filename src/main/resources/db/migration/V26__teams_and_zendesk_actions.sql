-- Two more kinds of Action: a message through a Microsoft Teams Workflows callback and
-- an article in a Zendesk Help Center. Like the last pair they need nothing but room in
-- the two lists of Action kinds: the Teams callback URL is the Action's secret, and what
-- Zendesk needs lives in the configuration object the Action already carries.

ALTER TABLE automation_rule_actions
    DROP CONSTRAINT automation_rule_actions_type_known,
    ADD CONSTRAINT automation_rule_actions_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG', 'NOTION', 'CONFLUENCE',
                               'MICROSOFT_TEAMS', 'ZENDESK'));

ALTER TABLE automation_action_runs
    DROP CONSTRAINT automation_action_runs_type_known,
    ADD CONSTRAINT automation_action_runs_type_known
        CHECK (action_type IN ('GITHUB_RELEASE', 'SLACK', 'EMAIL', 'PUBLIC_CHANGELOG', 'NOTION', 'CONFLUENCE',
                               'MICROSOFT_TEAMS', 'ZENDESK'));
