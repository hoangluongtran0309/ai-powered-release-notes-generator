package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void enforcesOneIntegrationPerProjectAndRepositoryUniquenessPerOrganization() {
        UUID firstOrganization = insertOrganization("First");
        UUID secondOrganization = insertOrganization("Second");
        UUID firstProject = insertProject(firstOrganization, "First project");
        UUID anotherFirstProject = insertProject(firstOrganization, "Another first project");
        UUID secondProject = insertProject(secondOrganization, "Second project");
        insertIntegration(firstOrganization, firstProject, "acme", "releaseflow", validNonce(), validCiphertext());

        assertThatThrownBy(() -> insertIntegration(
                firstOrganization, firstProject, "other", "repository", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIntegration(
                firstOrganization, anotherFirstProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertIntegration(
                secondOrganization, secondProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossTenantProjectLinksAndInvalidEncryptionEnvelope() {
        UUID firstOrganization = insertOrganization("First");
        UUID secondOrganization = insertOrganization("Second");
        UUID firstProject = insertProject(firstOrganization, "First project");

        assertThatThrownBy(() -> insertIntegration(
                secondOrganization, firstProject, "acme", "releaseflow", validNonce(), validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIntegration(
                firstOrganization, firstProject, "acme", "releaseflow", new byte[11], validCiphertext()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOrganization(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, ?, ?, 'en')",
                id,
                name,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private UUID insertProject(UUID organizationId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, organization_id, name, created_at) VALUES (?, ?, ?, ?)",
                id,
                organizationId,
                name,
                Timestamp.from(Instant.now())
        );
        return id;
    }

    private void insertIntegration(
            UUID organizationId,
            UUID projectId,
            String owner,
            String repository,
            byte[] nonce,
            byte[] ciphertext
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO github_integrations
                            (id, organization_id, project_id, repository_owner, repository_name,
                             webhook_id, secret_nonce, secret_ciphertext, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                organizationId,
                projectId,
                owner,
                repository,
                UUID.randomUUID(),
                nonce,
                ciphertext,
                Timestamp.from(Instant.now())
        );
    }

    private static byte[] validNonce() {
        return new byte[12];
    }

    private static byte[] validCiphertext() {
        return new byte[17];
    }
}
