package com.hoangluongtran0309.releaseflow.translation;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationResult;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.DeepLStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.hoangluongtran0309.releaseflow.support.TestChanges;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Release notes in English and Vietnamese, translated through a stand-in for DeepL. */
class MultilingualReleaseNoteIntegrationTest extends PostgreSqlIntegrationTest {

    private static final DeepLStub DEEPL = DeepLStub.start();
    private static final String NARRATIVES = """
            {"operator":"Watch the export queue.","contributor":"Use ExportJob.","end_user":"Download any table."}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private TranslationWorker worker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void deepLProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.translation.provider", () -> "deepl");
        registry.add("releaseflow.deepl.api-key", () -> "deepl-test-key");
        registry.add("releaseflow.deepl.base-url", DEEPL::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        DEEPL.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        DEEPL.reset();
        // Published releases reject DELETE by design; TRUNCATE bypasses row triggers.
        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM organization_translation_settings");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void approvalWritesANoteForEveryAudienceAndLanguageAndPublishingWaitsForTheTranslations() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        approve(owner, projectId, releaseId)
                .andExpect(jsonPath("$.notes.length()").value(6))
                .andExpect(jsonPath("$.notes[?(@.language == 'en')].translationStatus").value(everyItem(is("READY"))))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("PENDING"))))
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].content").value(everyItem(containsString(
                        "Tables export as CSV."))));
        assertThat(DEEPL.requests()).as("nothing is translated inside the approval").isEmpty();
        action(owner, projectId, releaseId, "publish")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("translations_not_ready"));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE releases SET status = 'PUBLISHED', published_at = now(), published_by = approved_by, "
                        + "publisher_name = approver_name WHERE id = ?", releaseId))
                .as("the database holds publication too")
                .isInstanceOf(DataIntegrityViolationException.class);
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("Translating 3 notes…")))
                .andExpect(content().string(containsString("disabled=\"disabled\">Publish 1.4.0</button>")))
                .andExpect(content().string(containsString("data-poll-url=\"" + releaseApi(projectId, releaseId) + "/notes\"")));

        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isFalse();

        assertThat(DEEPL.requests()).singleElement().satisfies(request -> {
            assertThat(request.sourceLanguage()).isEqualTo("EN");
            assertThat(request.targetLanguage()).isEqualTo("VI");
            assertThat(request.texts()).containsExactlyInAnyOrder("Tables export as CSV.", "Users asked.", "Streams rows.",
                    "Watch the export queue.", "Use ExportJob.", "Download any table.");
        });
        String notes = mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[*].translationStatus").value(everyItem(is("READY"))))
                .andReturn().getResponse().getContentAsString();
        List<String> operatorVi = JsonPath.read(notes, "$[?(@.audienceCode == 'operator' && @.language == 'vi')].content");
        assertThat(operatorVi.getFirst())
                .startsWith("# Bản phát hành 1.4.0")
                .contains("- **[VI] Tables export as CSV.** ([#1](https://github.com/acme/releaseflow/pull/1)) — [VI] Watch the export queue.")
                .contains("  - Lý do: [VI] Users asked.")
                .contains("  - Chi tiết: [VI] Streams rows.");
        List<String> operatorEn = JsonPath.read(notes, "$[?(@.audienceCode == 'operator' && @.language == 'en')].content");
        assertThat(operatorEn.getFirst()).contains("- **Tables export as CSV.**").contains("  - Why: Users asked.");

        action(owner, projectId, releaseId, "publish")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void anUnchangedSummaryIsTranslatedOnceAndTheCacheServesRepeatedTexts() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        summarizedChange(owner, projectId, 2, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);

        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isTrue();
        assertThat(worker.processOne()).isFalse();
        assertThat(DEEPL.requests()).as("the second change's texts all came from the cache").hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM translation_jobs WHERE status = 'SUCCEEDED'", Long.class))
                .isEqualTo(2);

        action(owner, projectId, releaseId, "return-to-draft").andExpect(status().isOk());
        approve(owner, projectId, releaseId)
                .andExpect(jsonPath("$.notes[*].translationStatus").value(everyItem(is("READY"))));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM translation_jobs", Long.class)).isEqualTo(2);
        assertThat(worker.processOne()).isFalse();
        assertThat(DEEPL.requests()).hasSize(1);
    }

    @Test
    void anEditedSummaryIsTranslatedAgain() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        UUID change = summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);
        worker.processOne();

        mockMvc.perform(put(releaseApi(projectId, releaseId) + "/changes/{changeId}/summary", change)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"whatChanged":"Tables and charts export as CSV.","whyChanged":"Users asked.",
                                 "technicalDetail":"","migrationStep":"","narratives":{}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[?(@.language == 'vi')].translationStatus").value(everyItem(is("PENDING"))));

        assertThat(worker.processOne()).isTrue();
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[*].translationStatus").value(everyItem(is("READY"))))
                .andExpect(jsonPath("$[?(@.language == 'vi')].content").value(everyItem(containsString(
                        "[VI] Tables and charts export as CSV."))));
        assertThat(DEEPL.requests()).hasSize(2);
        assertThat(DEEPL.requests().getLast().texts()).containsExactly("Tables and charts export as CSV.");
    }

    @Test
    void failedTranslationsAreRetriedByHand() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);

        DEEPL.fail(503, TranslationWorker.MAX_ATTEMPTS);
        for (int attempt = 1; attempt <= TranslationWorker.MAX_ATTEMPTS; attempt++) {
            makeDue();
            assertThat(worker.processOne()).isTrue();
        }
        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts, last_error FROM translation_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", TranslationWorker.MAX_ATTEMPTS)
                .containsEntry("last_error", TranslationException.UNAVAILABLE);
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[?(@.language == 'vi')].translationStatus").value(everyItem(is("FAILED"))));
        mockMvc.perform(get(releasePage(projectId, releaseId)).session(owner.session()))
                .andExpect(content().string(containsString("3 notes could not be translated.")))
                .andExpect(content().string(containsString("Retry translations")));
        action(owner, projectId, releaseId, "publish").andExpect(status().isConflict());

        mockMvc.perform(post(releasePage(projectId, releaseId) + "/translations/retry").session(owner.session()).with(csrf()))
                .andExpect(redirectedUrl(releasePage(projectId, releaseId) + "#release-notes"));
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[?(@.language == 'vi')].translationStatus").value(everyItem(is("PENDING"))));
        assertThat(worker.processOne()).isTrue();
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());
    }

    @Test
    void aRejectedTranslationFailsAtOnceAndAnEditedNoteIsReady() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);

        DEEPL.fail(456, 1);
        assertThat(worker.processOne()).isTrue();
        assertThat(jdbcTemplate.queryForMap("SELECT status, attempts FROM translation_jobs"))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 1);
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/translations/retry").session(owner.session()).with(csrf()))
                .andExpect(status().isOk());
        DEEPL.fail(456, 1);
        assertThat(worker.processOne()).isTrue();

        String notes = mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andReturn().getResponse().getContentAsString();
        List<String> failed = JsonPath.read(notes, "$[?(@.translationStatus == 'FAILED')].id");
        assertThat(failed).hasSize(3);
        for (String noteId : failed) {
            mockMvc.perform(put(releaseApi(projectId, releaseId) + "/notes/{noteId}", noteId)
                            .session(owner.session())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"# Bản phát hành 1.4.0\\n\\nXuất bảng dưới dạng CSV.\\n\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get(releaseApi(projectId, releaseId) + "/notes").session(owner.session()))
                .andExpect(jsonPath("$[*].translationStatus").value(everyItem(is("READY"))));
        action(owner, projectId, releaseId, "publish").andExpect(status().isOk());
    }

    @Test
    void retryingIsOnlyForAnApprovedRelease() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");

        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/translations/retry").session(owner.session()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("release_status_conflict"));
        mockMvc.perform(post(releaseApi(projectId, UUID.randomUUID()) + "/translations/retry").session(owner.session())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTranslationTablesKeepTheirInvariants() throws Exception {
        Owner owner = registerWithLanguages("owner@example.com", "en", "vi");
        UUID projectId = createProject(owner);
        UUID change = summarizedChange(owner, projectId, 1, "Tables export as CSV.");
        UUID releaseId = draftWithAllChanges(owner, projectId, "1.4.0");
        approve(owner, projectId, releaseId);

        UUID job = jdbcTemplate.queryForObject("SELECT id FROM translation_jobs", UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO translation_jobs (id, organization_id, project_id, change_id, source_language, target_language, "
                        + "input_hash, input, status, attempts, next_attempt_at, created_at) "
                        + "SELECT gen_random_uuid(), organization_id, project_id, change_id, source_language, target_language, "
                        + "input_hash, input, 'PENDING', 0, now(), now() FROM translation_jobs WHERE id = ?", job))
                .as("one job per change, language, and input")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE translation_jobs SET status = 'SUCCEEDED' WHERE id = ?", job))
                .as("a succeeded job has its output")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE release_audience_notes SET translation_status = 'DONE' WHERE release_id = ?", releaseId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO organization_translation_settings (organization_id, target_languages, updated_by, updater_name, "
                        + "updated_at) VALUES (?, '[]', ?, 'Mai', now())", UUID.randomUUID(), owner.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE organization_translation_settings SET target_languages = '[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]'"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.execute("TRUNCATE release_audience_notes, release_change_reviews, release_notes, release_changes, releases");
        jdbcTemplate.update("DELETE FROM changes WHERE id = ?", change);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM translation_jobs", Long.class)).isZero();
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE translation_jobs SET next_attempt_at = now() - interval '1 second' WHERE status = 'PENDING'");
    }

    private UUID summarizedChange(Owner owner, UUID projectId, int number, String whatChanged) {
        UUID change = TestChanges.insert(jdbcTemplate, owner.organizationId(), projectId, number,
                "feat: add export " + number, "FEATURE", false, false, null);
        TestChanges.summarize(jdbcTemplate, change, whatChanged, NARRATIVES);
        return change;
    }

    private ResultActions action(Owner owner, UUID projectId, UUID releaseId, String action) throws Exception {
        return mockMvc.perform(post(releaseApi(projectId, releaseId) + "/" + action).session(owner.session()).with(csrf()));
    }

    // Asks for review, approves every change as shown, then approves the release.
    private ResultActions approve(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        action(owner, projectId, releaseId, "request-review").andExpect(status().isOk());
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
        return action(owner, projectId, releaseId, "approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private UUID draftWithAllChanges(Owner owner, UUID projectId, String version) throws Exception {
        String body = mockMvc.perform(post("/api/projects/{projectId}/releases", projectId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"%s\"}".formatted(version)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID releaseId = UUID.fromString(JsonPath.read(body, "$.id"));
        mockMvc.perform(post(releaseApi(projectId, releaseId) + "/changes")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allAvailable\":true}"))
                .andExpect(status().isOk());
        return releaseId;
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

    private static String releasePage(UUID projectId, UUID releaseId) {
        return "/projects/" + projectId + "/releases/" + releaseId;
    }

    private Owner registerWithLanguages(String email, String... languages) throws Exception {
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
        Owner owner = new Owner(
                registration.organizationId(),
                registration.userId(),
                (MockHttpSession) login.getRequest().getSession(false)
        );
        mockMvc.perform(put("/api/organization/release-languages")
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetLanguages\":[\"" + String.join("\",\"", languages) + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetLanguages").value(contains((Object[]) languages)))
                .andExpect(jsonPath("$.translationEnabled").value(true));
        return owner;
    }

    private record Owner(UUID organizationId, UUID userId, MockHttpSession session) {
    }
}
