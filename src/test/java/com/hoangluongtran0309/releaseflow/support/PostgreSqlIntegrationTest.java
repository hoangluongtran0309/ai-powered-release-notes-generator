package com.hoangluongtran0309.releaseflow.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgreSqlTestConfiguration.class)
public abstract class PostgreSqlIntegrationTest {

    private static final String TEST_MASTER_KEY = generateTestMasterKey();

    @Autowired
    private JdbcTemplate supportJdbcTemplate;

    @DynamicPropertySource
    static void credentialProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.credentials.master-key", () -> TEST_MASTER_KEY);
        // Tests drive the change worker explicitly instead of racing its schedule.
        registry.add("releaseflow.processing.enabled", () -> "false");
        registry.add("releaseflow.translation.worker-enabled", () -> "false");
        registry.add("releaseflow.automation.worker-enabled", () -> "false");
        registry.add("releaseflow.automation.trigger-worker-enabled", () -> "false");
    }

    /** Every Organization owns audiences and categories, which must go before the Organization itself. */
    protected void deleteOrganizationSettings() {
        // A public entry points at the Action run that published it, and outlives it.
        supportJdbcTemplate.update("DELETE FROM public_changelog_entries");
        supportJdbcTemplate.update("DELETE FROM automation_action_runs");
        supportJdbcTemplate.update("DELETE FROM automation_runs");
        supportJdbcTemplate.update("DELETE FROM automation_publish_jobs");
        supportJdbcTemplate.update("DELETE FROM automation_rule_actions");
        supportJdbcTemplate.update("DELETE FROM automation_rules");
        supportJdbcTemplate.update("DELETE FROM translation_jobs");
        supportJdbcTemplate.update("DELETE FROM translation_cache");
        supportJdbcTemplate.update("DELETE FROM organization_translation_settings");
        supportJdbcTemplate.update("DELETE FROM category_suggestions");
        supportJdbcTemplate.update("DELETE FROM category_definitions");
        supportJdbcTemplate.update("DELETE FROM audience_definitions");
    }

    private static String generateTestMasterKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
