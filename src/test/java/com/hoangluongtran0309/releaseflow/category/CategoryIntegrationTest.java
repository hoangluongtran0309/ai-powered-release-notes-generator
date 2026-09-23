package com.hoangluongtran0309.releaseflow.category;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryIntegrationTest extends PostgreSqlIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE public_changelog_entries, automation_action_runs, automation_runs, automation_publish_jobs, automation_rule_actions, automation_rules, release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void registrationSeedsTheFormerFixedCategories() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        mockMvc.perform(get("/api/categories").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains(
                        "DOCUMENTATION", "FEATURE", "FIX", "MAINTENANCE", "PERFORMANCE", "UNKNOWN")))
                .andExpect(jsonPath("$[?(@.code == 'UNKNOWN')].group").value(contains("OTHER")))
                .andExpect(jsonPath("$[?(@.code == 'UNKNOWN')].systemCategory").value(contains(true)))
                .andExpect(jsonPath("$[?(@.code == 'PERFORMANCE')].group").value(contains("PERFORMANCE")))
                .andExpect(jsonPath("$[?(@.systemCategory == true)]").value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void administratorsCreateEditArchiveAndRestoreCategories() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        String created = create(admin, " audit-log ", "Audit log", "MAINTENANCE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.systemCategory").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        create(admin, "Audit Log", "Again", "FIX")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_code_taken"));
        create(admin, "9lives", "Cats", "OTHER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code", containsString("starting with a letter")));
        mockMvc.perform(post("/api/categories").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X\",\"displayName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.group").value("Choose a group."));

        update(admin, id, "Auditing", "FIX")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG"))
                .andExpect(jsonPath("$.displayName").value("Auditing"))
                .andExpect(jsonPath("$.group").value("FIX"));
        mockMvc.perform(delete("/api/categories/{id}", id).session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(post("/api/categories/{id}/unarchive", id).session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        String unknown = idOf(admin, "UNKNOWN");
        update(admin, unknown, "Unclassified", "OTHER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Unclassified"));
        update(admin, unknown, "Unclassified", "FIX")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_system"));
        mockMvc.perform(delete("/api/categories/{id}", unknown).session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_system"));
    }

    @Test
    void anArchivedCategoryIsNoLongerOfferedButChangesKeepIt() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        UUID projectId = createProject(admin);
        UUID change = TestChanges.insert(jdbcTemplate, admin.organizationId(), projectId, 1,
                "perf: cache previews", "PERFORMANCE", false, false, null);
        UUID other = TestChanges.insert(jdbcTemplate, admin.organizationId(), projectId, 2,
                "Tidy exports", "UNKNOWN", false, true, null);

        mockMvc.perform(delete("/api/categories/{id}", idOf(admin, "PERFORMANCE")).session(admin.session()).with(csrf()))
                .andExpect(status().isOk());

        review(admin, projectId, other, "performance")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_change_review"));
        mockMvc.perform(get("/api/projects/{projectId}/changes", projectId).session(admin.session()).param("category", "performance"))
                .andExpect(jsonPath("$[*].id").value(contains(change.toString())))
                .andExpect(jsonPath("$[0].categoryName").value("Performance"))
                .andExpect(jsonPath("$[0].categoryGroup").value("PERFORMANCE"));
        mockMvc.perform(get("/changes").session(admin.session()).param("project", projectId.toString()))
                .andExpect(content().string(containsString("<option value=\"PERFORMANCE\">Performance (archived)</option>")))
                .andExpect(content().string(not(containsString("<option value=\"PERFORMANCE\">Performance</option>"))));
    }

    @Test
    void aCustomCategoryIsReviewedAndLandsInItsGroupsSectionOfTheReleaseNote() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        create(admin, "SECURITY", "Security", "FIX").andExpect(status().isCreated());
        UUID projectId = createProject(admin);
        UUID change = TestChanges.insert(jdbcTemplate, admin.organizationId(), projectId, 1,
                "Rotate signing keys", "UNKNOWN", false, true, null);

        review(admin, projectId, change, "security")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SECURITY"))
                .andExpect(jsonPath("$.categoryName").value("Security"))
                .andExpect(jsonPath("$.categoryGroup").value("FIX"))
                .andExpect(jsonPath("$.classificationSource").value("HUMAN"));

        String releases = "/api/projects/" + projectId + "/releases";
        String release = mockMvc.perform(post(releases).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andReturn().getResponse().getContentAsString();
        String releasePath = releases + "/" + JsonPath.read(release, "$.id");
        mockMvc.perform(post(releasePath + "/changes").session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(releasePath + "/request-review").session(admin.session()).with(csrf()))
                .andExpect(jsonPath("$.preview[0].title").value("Fixes"))
                .andExpect(jsonPath("$.preview[0].items[0].categoryName").value("Security"));
        mockMvc.perform(put(releasePath + "/changes/{changeId}/decision", change).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"category\":\"security\",\"breaking\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(releasePath + "/approve").session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].content", containsString("## 🐛 Bug Fixes\n\n- **Rotate signing keys**")));
    }

    @Test
    void onlyAdministratorsOfTheOrganizationChangeTheCatalog() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");
        Admin other = registerAndLogin("other@example.com");
        String feature = idOf(admin, "FEATURE");
        MockHttpSession member = member(admin.organizationId());

        mockMvc.perform(get("/api/categories").session(member)).andExpect(status().isOk());
        mockMvc.perform(post("/api/categories").session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/categories/{id}", feature).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/category-suggestions").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/category-suggestions/{id}/decision", UUID.randomUUID()).session(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/categories").session(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/").session(member)).andExpect(content().string(not(containsString("href=\"/categories\""))));

        update(other, feature, "Taken", "FIX")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("category_not_found"));
        mockMvc.perform(delete("/api/categories/{id}", feature).session(other.session()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/category-suggestions/{id}/decision", UUID.randomUUID()).session(other.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("category_suggestion_not_found"));
    }

    @Test
    void theCategoriesPageManagesTheCatalog() throws Exception {
        Admin admin = registerAndLogin("owner@example.com");

        mockMvc.perform(get("/categories").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/categories\"")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"category-UNKNOWN\".*>System</span>.*")))
                .andExpect(content().string(containsString("No proposals are waiting.")));

        mockMvc.perform(post("/categories").session(admin.session()).with(csrf())
                        .param("code", "feature").param("displayName", "Feature again").param("group", "FEATURE"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("A category with this code already exists.")));
        mockMvc.perform(post("/categories").session(admin.session()).with(csrf())
                        .param("code", "security").param("displayName", "Security").param("group", "FIX"))
                .andExpect(redirectedUrl("/categories?saved"));
        String security = idOf(admin, "SECURITY");
        mockMvc.perform(post("/categories/{id}", security).session(admin.session()).with(csrf())
                        .param("displayName", "Security fixes").param("group", "FIX"))
                .andExpect(redirectedUrl("/categories?saved"));
        mockMvc.perform(post("/categories/{id}/archive", security).session(admin.session()).with(csrf()))
                .andExpect(redirectedUrl("/categories?archived"));
        mockMvc.perform(get("/categories").session(admin.session()))
                .andExpect(content().string(matchesPattern("(?s).*id=\"category-SECURITY\".*Security fixes.*>Archived</span>.*Restore.*")));
        mockMvc.perform(post("/categories/{id}/archive", idOf(admin, "UNKNOWN")).session(admin.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("A system category keeps its group")));
        mockMvc.perform(post("/categories/suggestions/{id}/decision", UUID.randomUUID()).session(admin.session()).with(csrf())
                        .param("decision", "REJECTED"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Category suggestion was not found.")));
    }

    private ResultActions create(Admin admin, String code, String name, String group) throws Exception {
        return mockMvc.perform(post("/api/categories").session(admin.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"displayName\":\"%s\",\"group\":\"%s\"}".formatted(code, name, group)));
    }

    private ResultActions update(Admin admin, String id, String name, String group) throws Exception {
        return mockMvc.perform(put("/api/categories/{id}", id).session(admin.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"%s\",\"group\":\"%s\"}".formatted(name, group)));
    }

    private ResultActions review(Admin admin, UUID projectId, UUID changeId, String category) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/review", projectId, changeId)
                .session(admin.session()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"%s\",\"breaking\":false}".formatted(category)));
    }

    private String idOf(Admin admin, String code) throws Exception {
        List<String> ids = JsonPath.read(mockMvc.perform(get("/api/categories").session(admin.session()))
                .andReturn().getResponse().getContentAsString(), "$[?(@.code == '" + code + "')].id");
        return ids.getFirst();
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
