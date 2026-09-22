package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectSensitivePathIntegrationTest extends PostgreSqlIntegrationTest {

    private static final int BASELINE_SIZE = 15;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM project_sensitive_paths");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aProjectStartsWithTheBaselineOnly() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);

        mockMvc.perform(get("/api/projects/{projectId}/sensitive-paths", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.toString()))
                .andExpect(jsonPath("$.baseline", hasSize(BASELINE_SIZE)))
                .andExpect(jsonPath("$.baseline[0]").value("**/security/**"))
                .andExpect(jsonPath("$.additions", empty()))
                .andExpect(jsonPath("$.effective", hasSize(BASELINE_SIZE)))
                .andExpect(jsonPath("$.updatedBy", nullValue()))
                .andExpect(jsonPath("$.updatedAt", nullValue()));
    }

    @Test
    void administratorsAddPatternsButNeverRemoveTheBaseline() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);

        replace(admin.session(), project, "[\" **/billing/** \", \"\", \"**/pom.xml\", \"**/billing/**\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additions").value(contains("**/billing/**", "**/pom.xml")))
                .andExpect(jsonPath("$.effective", hasSize(BASELINE_SIZE + 1)))
                .andExpect(jsonPath("$.effective[" + BASELINE_SIZE + "]").value("**/billing/**"))
                .andExpect(jsonPath("$.updatedBy").value("Mai Tran"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mockMvc.perform(get("/api/projects/{projectId}/sensitive-paths", project).session(admin.session()))
                .andExpect(jsonPath("$.additions").value(contains("**/billing/**", "**/pom.xml")));

        replace(admin.session(), project, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.additions", empty()))
                .andExpect(jsonPath("$.effective", hasSize(BASELINE_SIZE)))
                .andExpect(jsonPath("$.updatedBy").value("Mai Tran"));
    }

    @Test
    void rejectsInvalidPatternsAndKeepsTheSavedOnes() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);
        replace(admin.session(), project, "[\"**/billing/**\"]").andExpect(status().isOk());

        replace(admin.session(), project, "[\"src/[unclosed\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_sensitive_paths"))
                .andExpect(jsonPath("$.detail").value("Not a valid glob pattern: src/[unclosed"));
        replace(admin.session(), project, "[\"" + "a".repeat(257) + "\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_sensitive_paths"));
        String tooMany = String.join(",", IntStream.range(0, 101)
                .mapToObj(index -> "\"module" + index + "/**\"").toList());
        replace(admin.session(), project, "[" + tooMany + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("at most 100 patterns")));
        mockMvc.perform(put("/api/projects/{projectId}/sensitive-paths", project).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(get("/api/projects/{projectId}/sensitive-paths", project).session(admin.session()))
                .andExpect(jsonPath("$.additions").value(contains("**/billing/**")));
    }

    @Test
    void membersReadButOnlyAdministratorsOfTheOrganizationChange() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        Admin other = registerAndLogin("other@example.com");
        UUID project = createProject(admin);
        MockHttpSession member = member(admin.organizationId());

        mockMvc.perform(get("/api/projects/{projectId}/sensitive-paths", project).session(member))
                .andExpect(status().isOk());
        replace(member, project, "[\"**/billing/**\"]").andExpect(status().isForbidden());
        mockMvc.perform(post("/projects/{projectId}/sensitive-paths", project).session(member).with(csrf())
                        .param("additions", "**/billing/**"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/projects/{projectId}/sensitive-paths", project).session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No patterns have been added for this project.")))
                .andExpect(content().string(containsString("Only organization administrators can change these patterns.")))
                .andExpect(content().string(not(containsString("<textarea"))));

        mockMvc.perform(get("/api/projects/{projectId}/sensitive-paths", project).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        replace(other.session(), project, "[\"**/billing/**\"]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("project_not_found"));
        mockMvc.perform(get("/projects/{projectId}/sensitive-paths", project).session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Project was not found.")));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM project_sensitive_paths", Long.class)).isZero();
    }

    @Test
    void theSensitivePathsPageSavesOnePatternPerLine() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID project = createProject(admin);

        mockMvc.perform(get("/projects").session(admin.session()))
                .andExpect(content().string(containsString("href=\"/projects/" + project + "/sensitive-paths\"")));
        mockMvc.perform(get("/projects/{projectId}/sensitive-paths", project).session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<textarea")))
                .andExpect(content().string(containsString("**/db/migration/**")));

        mockMvc.perform(post("/projects/{projectId}/sensitive-paths", project).session(admin.session()).with(csrf())
                        .param("additions", "**/billing/**\r\n\r\ninfra/**\n"))
                .andExpect(redirectedUrl("/projects/" + project + "/sensitive-paths?saved"));
        mockMvc.perform(get("/projects/{projectId}/sensitive-paths", project).session(admin.session()).param("saved", ""))
                .andExpect(content().string(containsString("Patterns saved.")))
                .andExpect(content().string(containsString("**/billing/**\ninfra/**</textarea>")))
                .andExpect(content().string(matchesPattern("(?s).*Last changed by</span>\\s*<span>Mai Tran</span>.*")));

        mockMvc.perform(post("/projects/{projectId}/sensitive-paths", project).session(admin.session()).with(csrf())
                        .param("additions", "**/billing/**\nsrc/[unclosed"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Not a valid glob pattern: src/[unclosed")))
                .andExpect(content().string(containsString("**/billing/**\nsrc/[unclosed</textarea>")));
        mockMvc.perform(post("/projects/{projectId}/sensitive-paths", UUID.randomUUID()).session(admin.session())
                        .with(csrf()).param("additions", "**/billing/**"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Project was not found.")));
    }

    @Test
    void theDatabaseKeepsAdditionsBoundedAndTiedToTheirProject() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        Admin other = registerAndLogin("other@example.com");
        UUID project = createProject(admin);
        UUID owner = jdbcTemplate.queryForObject(
                "SELECT id FROM app_users WHERE organization_id = ?", UUID.class, admin.organizationId());
        UUID outsider = jdbcTemplate.queryForObject(
                "SELECT id FROM app_users WHERE organization_id = ?", UUID.class, other.organizationId());
        String hundredOne = "[" + String.join(",", Collections.nCopies(101, "\"x\"")) + "]";

        for (String globs : List.of("'{}'", "'\"x\"'", "'" + hundredOne + "'")) {
            assertThatThrownBy(() -> insertPolicy(project, admin.organizationId(), owner, globs))
                    .as(globs)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertPolicy(project, other.organizationId(), outsider, "'[]'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPolicy(project, admin.organizationId(), outsider, "'[]'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertPolicy(project, admin.organizationId(), owner, "'[\"**/billing/**\"]'");

        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", project);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM project_sensitive_paths", Long.class)).isZero();
    }

    private void insertPolicy(UUID project, UUID organization, UUID updater, String globs) {
        jdbcTemplate.update("INSERT INTO project_sensitive_paths (project_id, organization_id, additional_globs, "
                + "updated_by, updater_name, updated_at) VALUES (?, ?, " + globs + "::jsonb, ?, 'Mai', now())",
                project, organization, updater);
    }

    private ResultActions replace(MockHttpSession session, UUID project, String additions) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}/sensitive-paths", project).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"additions\":" + additions + "}"));
    }

    private UUID createProject(Admin admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private MockHttpSession member(UUID organizationId) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, 'member@example.com', ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(), organizationId, passwordEncoder.encode("member-password")
        );
        return login("member@example.com", "member-password");
    }

    private Admin registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        return new Admin(registration.organizationId(), login(email, "owner-password"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private record Admin(UUID organizationId, MockHttpSession session) {
    }
}
