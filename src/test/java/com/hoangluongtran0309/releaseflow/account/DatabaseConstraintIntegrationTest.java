package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        deleteAudiences();
        organizationRepository.deleteAll();
    }

    @Test
    void databaseEnforcesUniqueCanonicalEmail() {
        UUID organizationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, name, created_at, output_language) VALUES (?, ?, ?, 'en')",
                organizationId,
                "Acme",
                Timestamp.from(Instant.now())
        );
        insertUser(UUID.randomUUID(), organizationId, "owner@example.com");

        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), organizationId, "owner@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), organizationId, "Other@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID userId, UUID organizationId, String email) {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users
                            (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                organizationId,
                email,
                "bcrypt-hash",
                "Admin",
                "ADMIN",
                Timestamp.from(Instant.now())
        );
    }
}
