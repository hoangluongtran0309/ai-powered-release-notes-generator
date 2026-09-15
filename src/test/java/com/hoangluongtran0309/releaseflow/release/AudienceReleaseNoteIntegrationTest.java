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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class AudienceReleaseNoteIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String NARRATIVES = """
            {"operator":"Watch the export queue.","contributor":"Use ExportJob instead of TableExporter.",
             "end_user":"Download any table.","leadership":"Customers asked for this most."}""";
    private static final String OPERATOR_NOTE = """
            # Release 1.4.0

            ## What's New

            This release includes 1 change: 1 new feature.

            ## ✨ New Features

            - **Tables export as CSV.** ([#1](https://github.com/acme/releaseflow/pull/1)) — Watch the export queue.
              - Why: Users asked.
              - Detail: Streams rows.
            """;

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
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void approvalWritesADifferentNoteForEveryAudience() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", NARRATIVES);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/note-previews").session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].audienceCode").value(contains("contributor", "end_user", "operator")))
                .andExpect(jsonPath("$[2].content").value(OPERATOR_NOTE));

        approve(owner, projectId, releaseId)
                .andExpect(jsonPath("$.notes.length()").value(3))
                .andExpect(jsonPath("$.notes[*].audienceName").value(contains("Contributor", "End user", "Operator")))
                .andExpect(jsonPath("$.notes[0].content", containsString(
                        "([#1](https://github.com/acme/releaseflow/pull/1)) — Use ExportJob instead of TableExporter.\n"
                                + "  - Why: Users asked.\n  - Implementation: Streams rows.\n")))
                .andExpect(jsonPath("$.notes[1].content", containsString(
                        "## ✨ New Features\n\n- **Tables export as CSV.** — Download any table.\n")))
                .andExpect(jsonPath("$.notes[1].content", not(containsString("Why:"))))
                .andExpect(jsonPath("$.notes[2].content").value(OPERATOR_NOTE))
                .andExpect(jsonPath("$.notes[2].language").value("en"))
                .andExpect(jsonPath("$.notes[2].autoRerender").value(true));

        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$.length()").value(3));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT template_body_snapshot FROM release_audience_notes WHERE release_id = ? AND audience_code = 'operator'",
                String.class, releaseId
        )).contains("  - Detail: {{.}}");
    }

    @Test
    void aFourthAudienceGetsItsOwnNoteWithoutAnyCodeChange() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        mockMvc.perform(post("/api/audiences")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"leadership","displayName":"Leadership","communicationIntent":"Business impact.",
                                 "templateBody":"* {{whatChanged}}{{#narrative}} ({{.}}){{/narrative}}"}
                                """))
                .andExpect(status().isCreated());
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", NARRATIVES);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        approve(owner, projectId, releaseId)
                .andExpect(jsonPath("$.notes.length()").value(4))
                .andExpect(jsonPath("$.notes[2].audienceCode").value("leadership"))
                .andExpect(jsonPath("$.notes[2].content", containsString(
                        "## ✨ New Features\n\n* Tables export as CSV. (Customers asked for this most.)\n")));
    }

    @Test
    void notesCanBeEditedOnlyWhileTheReleaseIsApproved() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        editNote(owner, projectId, releaseId, UUID.randomUUID(), "# Mine")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));

        String approved = approveRemaining(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        UUID operatorNote = UUID.fromString(JsonPath.read(approved, "$.notes[2].id"));

        editNote(owner, projectId, releaseId, operatorNote, "# Release 1.4.0\r\n\r\nHand written.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[2].content").value("# Release 1.4.0\n\nHand written.\n"))
                .andExpect(jsonPath("$.notes[2].autoRerender").value(false))
                .andExpect(jsonPath("$.notes[2].lastEditorName").value("Mai Tran"))
                .andExpect(jsonPath("$.notes[1].autoRerender").value(true));
        editNote(owner, projectId, releaseId, operatorNote, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        editNote(owner, projectId, releaseId, UUID.randomUUID(), "# Other")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_note_not_found"));
    }

    @Test
    void editingASummaryRendersTheAutomaticNotesAgain() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        UUID outside = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 2,
                "fix: other", "FIX", false, false, null);
        UUID releaseId = createDraft(owner, projectId, "1.4.0");
        addChanges(owner, projectId, releaseId, "{\"changeIds\":[\"%s\"]}".formatted(change));

        editSummary(owner, projectId, releaseId, change, "Draft summary.", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        // A change without an AI summary gets one written by a person.
        editSummary(owner, projectId, releaseId, change, "Tables export as CSV.", Map.of("end_user", "Download any table."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].neutralSummary.whatChanged").value("Tables export as CSV."))
                .andExpect(jsonPath("$.changes[0].audienceNarratives.end_user").value("Download any table."))
                .andExpect(jsonPath("$.changes[0].summaryEditorName").value("Mai Tran"))
                .andExpect(jsonPath("$.changes[0].contentLanguage").value("en"));
        editSummary(owner, projectId, releaseId, outside, "Not in this release.", Map.of())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("change_not_found"));
        editSummary(owner, projectId, releaseId, change, "  ", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        String approved = approveRemaining(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(approved, "$.notes[1].content")).contains("- **Tables export as CSV.** — Download any table.");
        UUID operatorNote = UUID.fromString(JsonPath.read(approved, "$.notes[2].id"));
        editNote(owner, projectId, releaseId, operatorNote, "# Kept by hand").andExpect(status().isOk());

        editSummary(owner, projectId, releaseId, change, "Any table exports as CSV.",
                Map.of("end_user", "Save tables as spreadsheets.", "unknown_audience", "Dropped."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].audienceNarratives.unknown_audience").doesNotExist())
                .andExpect(jsonPath("$.notes[0].content", containsString("- **Any table exports as CSV.** ([#1]")))
                .andExpect(jsonPath("$.notes[1].content", containsString("- **Any table exports as CSV.** — Save tables as spreadsheets.")))
                .andExpect(jsonPath("$.notes[2].content").value("# Kept by hand\n"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary_editor_name FROM changes WHERE id = ?", String.class, change)).isEqualTo("Mai Tran");
    }

    @Test
    void returningToDraftDropsTheNotesAndApprovingAgainWritesNewOnes() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);

        action(owner, projectId, releaseId, "return-to-draft")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM release_audience_notes", Integer.class)).isZero();

        approve(owner, projectId, releaseId).andExpect(jsonPath("$.notes.length()").value(3));
    }

    @Test
    void publishingFreezesTheNotesWhichCanBeDownloaded() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, "Tables export as CSV.", NARRATIVES);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0/rc 1");
        String approved = approve(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        UUID operatorNote = UUID.fromString(JsonPath.read(approved, "$.notes[2].id"));
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());

        editNote(owner, projectId, releaseId, operatorNote, "# Too late")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_published"));
        editSummary(owner, projectId, releaseId, change, "Too late.", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_published"));

        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes/{noteId}/download", operatorNote)
                        .session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"1.4.0-rc-1-operator.md\""))
                .andExpect(content().string(containsString("# Release 1.4.0/rc 1\n")))
                .andExpect(content().string(containsString("— Watch the export queue.")));
    }

    @Test
    void approvedReleasesWithoutNotesCannotBePublished() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);
        jdbcTemplate.update("DELETE FROM release_audience_notes WHERE release_id = ?", releaseId);

        action(owner, projectId, releaseId, "publish")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_notes_missing"));
    }

    @Test
    void aTemplateThatCannotRenderBlocksApproval() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        // Only a template changed outside the application can fail; saving validates it.
        jdbcTemplate.update("UPDATE audience_definitions SET template_body = '{{userImpact}}' WHERE code = 'end_user'");
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());

        decideAllAndApprove(owner, projectId, releaseId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_note_render_failed"))
                .andExpect(jsonPath("$.detail", containsString("End user")));
        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        mockMvc.perform(get("/projects/{p}/releases/{r}", projectId, releaseId).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The template of the End user audience could not be rendered.")));
    }

    @Test
    void anAudienceWithNotesCannotBeDeleted() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1, "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        String approved = approve(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        String operator = JsonPath.read(approved, "$.notes[2].audienceId");

        mockMvc.perform(delete("/api/audiences/{id}", operator).session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("audience_in_use"));
        mockMvc.perform(put("/api/audiences/{id}", operator)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ops\",\"templateBody\":\"{{whatChanged}}\"}"))
                .andExpect(status().isOk());
        // The note keeps the name and template it was written with.
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[2].audienceName").value("Operator"))
                .andExpect(jsonPath("$[2].content", containsString("- **add export** ([#1]")));
    }

    @Test
    void notesStayInsideTheTenant() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        Owner other = registerAndLogin("other@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        String approved = approve(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        UUID note = UUID.fromString(JsonPath.read(approved, "$.notes[0].id"));

        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(other.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("release_not_found"));
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes/{noteId}/download", note).session(other.session()))
                .andExpect(status().isNotFound());
        editNote(other, projectId, releaseId, note, "# Taken").andExpect(status().isNotFound());
        editSummary(other, projectId, releaseId, change, "Taken.", Map.of()).andExpect(status().isNotFound());
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[0].autoRerender").value(true));
    }

    @Test
    void aReleasePublishedBeforeAudiencesIsShownFromItsLegacyNote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID releaseId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO releases (id, organization_id, project_id, version, status, created_at, updated_at,
                                              published_at, published_by, publisher_name)
                        VALUES (?, ?, ?, '1.0.0', 'PUBLISHED', now(), now(), now(), ?, 'Mai Tran')
                        """, releaseId, owner.organizationId(), projectId, owner.userId());
        jdbcTemplate.update("""
                        INSERT INTO release_notes (release_id, organization_id, project_id, version, sections, markdown, published_at)
                        VALUES (?, ?, ?, '1.0.0', '[{"title":"Features","items":[]}]', '# 1.0.0\n', now())
                        """, releaseId, owner.organizationId(), projectId);

        mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value("# 1.0.0\n"))
                .andExpect(jsonPath("$.preview[0].title").value("Features"))
                .andExpect(jsonPath("$.notes.length()").value(0));
        mockMvc.perform(get("/projects/{p}/releases/{r}", projectId, releaseId).session(owner.session()))
                .andExpect(view().name("release-note"))
                .andExpect(content().string(containsString("id=\"legacy-note\"")))
                .andExpect(content().string(not(containsString("id=\"release-notes\""))));
    }

    @Test
    void theReleasePageEditsSummariesAndNotes() throws Exception {
        Owner owner = registerAndLogin("owner@example.com");
        UUID projectId = createProject(owner);
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, 1,
                "feat: add export", "FEATURE", false, false, null);
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        String releasePath = "/projects/" + projectId + "/releases/" + releaseId;
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());

        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("id=\"summary-" + change + "\"")))
                .andExpect(content().string(containsString("name=\"narratives[end_user]\"")))
                .andExpect(content().string(containsString("aria-label=\"Release note preview\"")));
        mockMvc.perform(post(releasePath + "/changes/" + change + "/summary").session(owner.session()).with(csrf())
                        .param("whatChanged", "")
                        .param("narratives[end_user]", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Describe what changed.")));
        mockMvc.perform(post(releasePath + "/changes/" + change + "/summary").session(owner.session()).with(csrf())
                        .param("whatChanged", "Tables export as <b>CSV</b>.")
                        .param("whyChanged", "Users asked.")
                        .param("narratives[end_user]", "Download any table.\r\n")
                        .param("narratives[operator]", ""))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString("Last written by Mai Tran.")))
                .andExpect(content().string(containsString("<strong>Tables export as &lt;b&gt;CSV&lt;/b&gt;.</strong> — Download any table.")));

        String approved = approveRemaining(owner, projectId, releaseId).andReturn().getResponse().getContentAsString();
        UUID endUserNote = UUID.fromString(JsonPath.read(approved, "$.notes[1].id"));
        mockMvc.perform(post(releasePath + "/notes/" + endUserNote).session(owner.session()).with(csrf())
                        .param("content", "# Release 1.4.0\r\n\r\nFor everyone."))
                .andExpect(redirectedUrl(releasePath));
        mockMvc.perform(get(releasePath).session(owner.session()))
                .andExpect(content().string(containsString(">Manual</span>")))
                .andExpect(content().string(containsString("Edited by Mai Tran")))
                .andExpect(content().string(containsString("<p>For everyone.</p>")));
        mockMvc.perform(post(releasePath + "/notes/" + endUserNote).session(owner.session()).with(csrf())
                        .param("content", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("A release note cannot be empty.")));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT audience_narratives ->> 'end_user' FROM changes WHERE id = ?", String.class, change
        )).isEqualTo("Download any table.");
    }

    private ResultActions editNote(Owner owner, UUID projectId, UUID releaseId, UUID noteId, String content) throws Exception {
        return mockMvc.perform(put(releaseApi(projectId, releaseId) + "/notes/{noteId}", noteId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"%s\"}".formatted(content.replace("\r", "\\r").replace("\n", "\\n"))));
    }

    private ResultActions editSummary(
            Owner owner,
            UUID projectId,
            UUID releaseId,
            UUID changeId,
            String whatChanged,
            Map<String, String> narratives
    ) throws Exception {
        StringBuilder json = new StringBuilder("{");
        narratives.forEach((code, text) -> json.append(json.length() > 1 ? "," : "")
                .append('"').append(code).append("\":\"").append(text).append('"'));
        json.append('}');
        return mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/summary", changeId)
                .session(owner.session())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"whatChanged":"%s","whyChanged":"Users asked.","technicalDetail":"","migrationStep":"",
                         "narratives":%s}""".formatted(whatChanged, json)));
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action)
                .session(owner.session())
                .with(csrf()));
    }

    private ResultActions approve(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
        return approveRemaining(owner, projectId, releaseId);
    }

    private ResultActions approveRemaining(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        return decideAllAndApprove(owner, projectId, releaseId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt", notNullValue()));
    }

    // Approves every change as shown, then asks to approve the release.
    private ResultActions decideAllAndApprove(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        String release = mockMvc.perform(get(releaseApi(projectId, releaseId)).session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> changes = JsonPath.read(release, "$.changes");
        for (Map<String, Object> change : changes) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/decision", change.get("id"))
                            .session(owner.session())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"action\":\"APPROVE\",\"category\":\"%s\",\"breaking\":%s}".formatted(
                                    change.get("category").toString().toLowerCase(Locale.ROOT), change.get("breaking"))))
                    .andExpect(status().isOk());
        }
        return action(owner, projectId, releaseId, "approve");
    }

    private UUID createDraft(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version) throws Exception {
        UUID releaseId = createDraft(owner, projectId, version);
        addChanges(owner, projectId, releaseId, "{\"allAvailable\":true}");
        return releaseId;
    }

    private void addChanges(Owner owner, UUID projectId, UUID releaseId, String body) throws Exception {
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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

    private static String releaseApi(UUID projectId, UUID releaseId) {
        return "/api/projects/" + projectId + "/releases/" + releaseId;
    }

    private Owner registerAndLogin(String email) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName(email);
        request.setDisplayName("Mai Tran");
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
