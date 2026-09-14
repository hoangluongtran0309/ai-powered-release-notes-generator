package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM organization_invitations");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void acceptsOnlyAdminAndMemberRoles() {
        UUID organization = insertOrganization();

        assertThatCode(() -> insertUser(organization, "ADMIN")).doesNotThrowAnyException();
        assertThatCode(() -> insertUser(organization, "MEMBER")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertUser(organization, "OWNER")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsInvitationsCanonicalHashedAndInsideTheirTenant() {
        UUID organization = insertOrganization();
        UUID other = insertOrganization();
        UUID admin = insertUser(organization, "ADMIN");
        UUID outsider = insertUser(other, "ADMIN");

        assertThatCode(() -> insertInvitation(organization, "a@example.com", HASH, "PENDING", admin))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertInvitation(organization, "a@example.com", "b".repeat(64), "PENDING", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertInvitation(organization, "a@example.com", "c".repeat(64), "EXPIRED", admin))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> insertInvitation(organization, "b@example.com", HASH, "PENDING", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInvitation(organization, "B@example.com", "d".repeat(64), "PENDING", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInvitation(organization, "c@example.com", "raw-token", "PENDING", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInvitation(organization, "d@example.com", "e".repeat(64), "PENDING", outsider))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInvitation(organization, "e@example.com", "f".repeat(64), "ACCEPTED", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertInvitation(organization, "f@example.com", "0".repeat(64), "REVOKED", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migratesExistingOwnersToAdministrators() {
        String schema = "v9_role_check";
        try {
            migrate(schema, "8");
            UUID organization = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + schema + ".organizations (id, name, created_at) VALUES (?, 'Old', now())",
                    organization);
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".app_users (id, organization_id, email, password_hash, display_name, role, created_at)"
                            + " VALUES (?, ?, 'owner@example.com', 'hash', 'Owner', 'OWNER', now())",
                    UUID.randomUUID(), organization
            );

            migrate(schema, "9");

            assertThat(jdbcTemplate.queryForObject("SELECT role FROM " + schema + ".app_users", String.class))
                    .isEqualTo("ADMIN");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void insertInvitation(UUID organization, String email, String hash, String status, UUID createdBy) {
        jdbcTemplate.update(
                """
                        INSERT INTO organization_invitations
                            (id, organization_id, email, token_hash, status, expires_at, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, now() + interval '7 days', ?, now(), now())
                        """,
                UUID.randomUUID(), organization, email, hash, status, createdBy
        );
    }

    private UUID insertUser(UUID organization, String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, 'hash', 'User', ?, now())
                        """,
                id, organization, id + "@example.com", role
        );
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, created_at) VALUES (?, 'Organization', now())", id);
        return id;
    }
}
