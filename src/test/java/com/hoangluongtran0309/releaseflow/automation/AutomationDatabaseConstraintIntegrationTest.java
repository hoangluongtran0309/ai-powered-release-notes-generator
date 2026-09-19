package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules the database keeps, whatever asks it to break them. */
class AutomationDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE automation_action_runs, automation_runs, automation_publish_jobs,"
                + " release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM automation_rule_actions");
        jdbcTemplate.update("DELETE FROM automation_rules");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void keepsOneActiveRuleNamePerOrganizationWhateverTheCasing() {
        UUID organization = insertOrganization();
        UUID other = insertOrganization();
        UUID rule = insertRule(organization, "Announce");

        assertThatThrownBy(() -> insertRule(organization, "announce"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertRule(other, "Announce")).doesNotThrowAnyException();

        // An archived rule keeps its runs but gives its name back.
        jdbcTemplate.update("UPDATE automation_rules SET active = FALSE, enabled = FALSE WHERE id = ?", rule);
        assertThatCode(() -> insertRule(organization, "Announce")).doesNotThrowAnyException();
    }

    @Test
    void refusesARuleThatIsArchivedAndStillEnabled() {
        UUID organization = insertOrganization();
        UUID rule = insertRule(organization, "Announce");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_rules SET active = FALSE, enabled = TRUE WHERE id = ?", rule))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tiesEveryRuleAndActionToOneTenant() {
        UUID organization = insertOrganization();
        UUID other = insertOrganization();
        UUID project = insertProject(other);
        UUID rule = insertRule(organization, "Announce");

        // A rule cannot watch another Organization's project.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_rules SET project_id = ? WHERE id = ?", project, rule))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Nor deliver to another Organization's audience.
        assertThatThrownBy(() -> insertAction(rule, organization, 0, insertAudience(other), "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTheActionsOfARuleInOneOrderAndItsSecretWhole() {
        UUID organization = insertOrganization();
        UUID rule = insertRule(organization, "Announce");
        UUID audience = insertAudience(organization);
        insertAction(rule, organization, 0, audience, "{}");

        assertThatThrownBy(() -> insertAction(rule, organization, 0, audience, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAction(rule, organization, -1, audience, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAction(rule, organization, 1, audience, "[]"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_rule_actions SET secret_nonce = ? WHERE rule_id = ?",
                new byte[] {1, 2, 3}, rule))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertAction(rule, organization, 1, audience, "{}")).doesNotThrowAnyException();
    }

    @Test
    void refusesAnActionKindOrStatusNothingCanCarryOut() {
        UUID organization = insertOrganization();
        UUID rule = insertRule(organization, "Announce");
        UUID audience = insertAudience(organization);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO automation_rule_actions
                            (id, rule_id, organization_id, position, action_type, audience_id, target_language)
                        VALUES (?, ?, ?, 0, 'TELEPATHY', ?, 'en')
                        """,
                UUID.randomUUID(), rule, organization, audience))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_rules SET trigger_type = 'SCHEDULED_CRON' WHERE id = ?", rule))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesARunThatContradictsItself() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID release = insertRelease(organization, project);
        UUID rule = insertRule(organization, "Announce");

        // A publication names no requester; running by hand always does.
        assertThatThrownBy(() -> insertRun(organization, project, release, rule, "RELEASE_PUBLISHED", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRun(organization, project, release, rule, "MANUAL", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID run = insertRun(organization, project, release, rule, "RELEASE_PUBLISHED", null);
        // A publication fires each rule once, and one request is one run.
        assertThatThrownBy(() -> insertRun(organization, project, release, rule, "RELEASE_PUBLISHED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID request = UUID.randomUUID();
        insertRun(organization, project, release, rule, "MANUAL", request);
        assertThatThrownBy(() -> insertRun(organization, project, release, rule, "MANUAL", request))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A run that has not finished has no end, and a finished one does.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_runs SET status = 'SUCCEEDED' WHERE id = ?", run))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE automation_runs SET status = 'SUCCEEDED', completed_at = now() WHERE id = ?", run))
                .doesNotThrowAnyException();
    }

    @Test
    void keepsAnActionRunExplainingItself() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID release = insertRelease(organization, project);
        UUID rule = insertRule(organization, "Announce");
        UUID audience = insertAudience(organization);
        UUID action = insertAction(rule, organization, 0, audience, "{}");
        UUID run = insertRun(organization, project, release, rule, "RELEASE_PUBLISHED", null);
        UUID actionRun = insertActionRun(run, organization, action, audience, 0);

        assertThatThrownBy(() -> insertActionRun(run, organization, action, audience, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Only a delivery that failed carries a reason, and only a finished one has an end.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_action_runs SET status = 'FAILED', completed_at = now() WHERE id = ?", actionRun))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE automation_action_runs SET error_code = 'slack_rejected' WHERE id = ?", actionRun))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> jdbcTemplate.update(
                """
                        UPDATE automation_action_runs
                        SET status = 'FAILED', error_code = 'slack_rejected', completed_at = now()
                        WHERE id = ?
                        """, actionRun))
                .doesNotThrowAnyException();
    }

    @Test
    void announcesEachPublishedReleaseToAutomationOnce() {
        UUID organization = insertOrganization();
        UUID project = insertProject(organization);
        UUID release = insertRelease(organization, project);

        insertPublishJob(organization, project, release);
        assertThatThrownBy(() -> insertPublishJob(organization, project, release))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertPublishJob(UUID organization, UUID project, UUID release) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_publish_jobs
                            (id, organization_id, project_id, release_id, status, attempts, next_attempt_at, created_at)
                        VALUES (?, ?, ?, ?, 'PENDING', 0, now(), now())
                        """,
                id, organization, project, release
        );
        return id;
    }

    private UUID insertActionRun(UUID run, UUID organization, UUID action, UUID audience, int position) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_action_runs
                            (id, run_id, organization_id, rule_action_id, position, action_type, audience_id,
                             audience_name_snapshot, language_snapshot, note_content_snapshot, status)
                        VALUES (?, ?, ?, ?, ?, 'SLACK', ?, 'End user', 'en', 'A note.', 'PENDING')
                        """,
                id, run, organization, action, position, audience
        );
        return id;
    }

    private UUID insertRun(UUID organization, UUID project, UUID release, UUID rule, String trigger, UUID request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_runs
                            (id, rule_id, organization_id, project_id, release_id, rule_name_snapshot,
                             release_version_snapshot, trigger_type, request_id, status, created_at)
                        VALUES (?, ?, ?, ?, ?, 'Announce', '1.4.0', ?, ?, 'PENDING', now())
                        """,
                id, rule, organization, project, release, trigger, request
        );
        return id;
    }

    private UUID insertAction(UUID rule, UUID organization, int position, UUID audience, String configuration) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_rule_actions
                            (id, rule_id, organization_id, position, action_type, audience_id, target_language,
                             configuration)
                        VALUES (?, ?, ?, ?, 'SLACK', ?, 'en', ?::jsonb)
                        """,
                id, rule, organization, position, audience, configuration
        );
        return id;
    }

    private UUID insertRule(UUID organization, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO automation_rules
                            (id, organization_id, name, trigger_type, enabled, active, created_at, updated_at)
                        VALUES (?, ?, ?, 'RELEASE_PUBLISHED', FALSE, TRUE, now(), now())
                        """,
                id, organization, name
        );
        return id;
    }

    private UUID insertAudience(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO audience_definitions
                            (id, organization_id, code, display_name, communication_intent, template_body, preset,
                             created_at, updated_at)
                        VALUES (?, ?, ?, 'End user', 'Plain language.', '- **{{whatChanged}}**', FALSE, now(), now())
                        """,
                id, organization, "a" + id.toString().replace("-", "").substring(0, 12)
        );
        return id;
    }

    private UUID insertRelease(UUID organization, UUID project) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO releases (id, organization_id, project_id, version, status, created_at, updated_at)
                        VALUES (?, ?, ?, '1.4.0', 'DRAFT', now(), now())
                        """,
                id, organization, project
        );
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, 'Organization', now(), 'en')",
                id
        );
        return id;
    }

    private UUID insertProject(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, 'Project', now())",
                id, organizationId
        );
        return id;
    }
}
