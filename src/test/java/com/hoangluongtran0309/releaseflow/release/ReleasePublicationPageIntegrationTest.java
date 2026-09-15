package com.hoangluongtran0309.releaseflow.release;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ReleasePublicationPageIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM github_integrations");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void reviewsApprovesAndPublishesFromTheReleasePage() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        String releasePath = createDraft(owner, projectId);

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("release"))
                .andExpect(content().string(containsString("Add at least one change before requesting review.")))
                .andExpect(content().string(matchesPattern("(?s).*Planned for\\s*<time[^>]*>1 Oct 2099, 09:00 UTC</time>.*")))
                .andExpect(content().string(containsString("value=\"2099-10-01T09:00\"")));
        mockMvc.perform(post(releasePath + "/request-review").session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Add at least one change to this release first.")));
        mockMvc.perform(post(releasePath + "/publish").session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Approve this release before publishing it.")));

        UUID feature = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat(ui): add the *inbox*", "FEATURE", false, false, null);
        UUID unknown = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "Tidy the exporter", "UNKNOWN", false, true, null);
        mockMvc.perform(post(releasePath + "/changes").session(owner.session()).with(csrf()).param("allAvailable", "true"))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(post(releasePath + "/request-review").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePath));

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(view().name("release"))
                .andExpect(content().string(containsString("aria-current=\"step\">In review</li>")))
                .andExpect(content().string(containsString("0 of 2 changes reviewed")))
                .andExpect(content().string(matchesPattern("(?s).*<button type=\"submit\" class=\"btn btn-primary btn-sm\" disabled=\"disabled\">Approve 1\\.4\\.0</button>.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*id=\"review-" + unknown + "\".*disabled=\"disabled\">Approve as shown</button>.*An Unknown change needs a category.*")))
                .andExpect(content().string(not(containsString("Add all available"))))
                .andExpect(content().string(not(containsString("Save details"))));

        // A page that showed another classification cannot approve this one.
        mockMvc.perform(post(releasePath + "/changes/" + feature + "/decision").session(owner.session()).with(csrf())
                        .param("action", "APPROVE").param("category", "fix").param("breaking", "false"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("classification changed since you loaded it")));
        mockMvc.perform(post(releasePath + "/changes/" + feature + "/decision").session(owner.session()).with(csrf())
                        .param("category", "feature"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(releasePath + "/changes/" + feature + "/decision").session(owner.session()).with(csrf())
                        .param("action", "APPROVE").param("category", "feature").param("breaking", "false")
                        .param("note", "Looks right."))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(post(releasePath + "/changes/" + unknown + "/decision").session(owner.session()).with(csrf())
                        .param("action", "EDIT").param("category", "maintenance"))
                .andExpect(redirectedUrl(releasePath));

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("2 of 2 changes reviewed")))
                .andExpect(content().string(containsString("Looks right.")))
                .andExpect(content().string(matchesPattern("(?s).*id=\"review-" + unknown + "\".*>Edited</span>.*by Mai Tran.*")))
                .andExpect(content().string(containsString("<button type=\"submit\" class=\"btn btn-primary btn-sm\">Approve 1.4.0</button>")));
        mockMvc.perform(post(releasePath + "/approve").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("aria-current=\"step\">Approved</li>")))
                .andExpect(content().string(matchesPattern("(?s).*Approved by <span class=\"font-semibold\">Mai Tran</span>.*")))
                .andExpect(content().string(containsString("Publish 1.4.0")))
                .andExpect(content().string(not(containsString("Approve as shown"))))
                .andExpect(content().string(matchesPattern(
                        "(?s).*id=\"release-notes\".*id=\"note-contributor\".*id=\"note-end_user\".*id=\"note-operator\".*")))
                .andExpect(content().string(containsString(">Automatic</span>")))
                .andExpect(content().string(containsString("Edit Markdown")))
                .andExpect(content().string(containsString("Saving makes this note manual")));

        mockMvc.perform(post(releasePath + "/publish").session(owner.session()).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(view().name("release-note"))
                .andExpect(content().string(matchesPattern("(?s).*Published by <span class=\"font-semibold\">Mai Tran</span>.*")))
                .andExpect(content().string(matchesPattern("(?s).*Approved by <span class=\"font-semibold\">Mai Tran</span>.*")))
                .andExpect(content().string(containsString("<h2>✨ New Features</h2>")))
                .andExpect(content().string(containsString("<h2>🔧 Maintenance</h2>")))
                .andExpect(content().string(containsString("<strong>add the *inbox*</strong>")))
                .andExpect(content().string(containsString("# Release 1.4.0\n\nExports and clearer config.\n\n## What")))
                .andExpect(content().string(containsString("- **add the \\*inbox\\*** ([#1](")))
                .andExpect(content().string(containsString("/notes/")))
                .andExpect(content().string(containsString(">Download</a>")))
                .andExpect(content().string(containsString("x-data=\"copyText()\"")))
                .andExpect(content().string(not(containsString("Save details"))))
                .andExpect(content().string(not(containsString("Discard this release"))))
                .andExpect(content().string(not(containsString("Add all available"))));

        mockMvc.perform(post(releasePath).session(owner.session()).with(csrf()).param("version", "9.9.9"))
                .andExpect(status().isConflict())
                .andExpect(view().name("release-note"))
                .andExpect(content().string(containsString("This release is published.")));

        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(content().string(containsString("href=\"" + releasePath + "\">1.4.0</a>")))
                .andExpect(content().string(matchesPattern("(?s).*<span>Published</span>\\s*<span class=\"badge badge-sm\">1</span>.*")))
                .andExpect(content().string(containsString("Create a release")));
        mockMvc.perform(get("/releases").session(owner.session()).param("status", "draft"))
                .andExpect(content().string(containsString("No releases have this status.")));
        mockMvc.perform(get("/releases").session(owner.session()).param("status", "published"))
                .andExpect(content().string(containsString("href=\"" + releasePath + "\">1.4.0</a>")));
    }

    @Test
    void rejectsAChangeAndReturnsAReleaseToDraftFromThePage() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID kept = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add the inbox", "FEATURE", false, false, null);
        UUID rejected = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix!: rename config keys", "FIX", true, false, owner.userId());
        String releasePath = createDraft(owner, projectId);
        mockMvc.perform(post(releasePath + "/changes").session(owner.session()).with(csrf()).param("allAvailable", "true"))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(post(releasePath + "/request-review").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePath));

        mockMvc.perform(post(releasePath + "/changes/" + rejected + "/remove").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("0 of 1 changes reviewed")))
                .andExpect(content().string(not(containsString("id=\"review-" + rejected + "\""))))
                .andExpect(content().string(not(containsString("id=\"breaking-warning\""))));

        mockMvc.perform(post(releasePath + "/return-to-draft").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("aria-current=\"step\">Draft</li>")))
                .andExpect(content().string(containsString("name=\"changeIds\" value=\"" + rejected + "\"")))
                .andExpect(content().string(containsString("id=\"included-" + kept + "\"")));
        mockMvc.perform(post(releasePath + "/return-to-draft").session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Only a release that is in review or approved can return to draft.")));

        mockMvc.perform(post(releasePath + "/schedule").session(owner.session()).with(csrf())
                        .param("plannedReleaseAt", "2099-12-24T18:30"))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get("/releases").session(owner.session()))
                .andExpect(content().string(matchesPattern("(?s).*planned\\s*<time[^>]*>24 Dec 2099, 18:30 UTC</time>.*")));
        mockMvc.perform(post(releasePath + "/schedule").session(owner.session()).with(csrf())
                        .param("plannedReleaseAt", "not a date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Enter the planned release time as an ISO-8601 date and time")));
        mockMvc.perform(post(releasePath + "/schedule").session(owner.session()).with(csrf())
                        .param("plannedReleaseAt", ""))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(not(containsString("Planned for"))));

        mockMvc.perform(post(releasePath + "/discard").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl("/releases?project=" + projectId));
    }

    private String createDraft(Owner owner, UUID projectId) throws Exception {
        MvcResult created = mockMvc.perform(post("/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .param("version", "1.4.0")
                        .param("summary", "Exports and clearer config.")
                        .param("plannedReleaseAt", "2099-10-01T09:00"))
                .andExpect(status().isFound())
                .andReturn();
        return created.getResponse().getRedirectedUrl();
    }

    private UUID createProject(Owner owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private Owner registerAndLogin(String email, String displayName) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName(displayName);
        request.setEmail(email);
        request.setPassword("owner-password");
        RegistrationResult registration = registrationService.register(request);
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        return new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
