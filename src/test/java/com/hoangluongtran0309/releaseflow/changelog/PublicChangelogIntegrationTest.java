package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.automation.AutomationIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The one place a release note goes that ReleaseFlow serves itself. */
class PublicChangelogIntegrationTest extends AutomationIntegrationTestBase {

    @Test
    void publishesAReleaseNoteForAnybodyToRead() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, changelogRule("Publish", projectId, endUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actions[0].actionType").value("PUBLIC_CHANGELOG"))
                .andExpect(jsonPath("$.actions[0].secretConfigured").value(false)));
        enable(owner, ruleId).andExpect(status().isOk());

        publishedRelease(owner, projectId, "1.4.0");
        deliverEverything();

        String slug = slug(owner);
        // No session, no cookie, nothing but what was published.
        MvcResult page = mockMvc.perform(get("/changelog/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(containsString("1.4.0")))
                .andExpect(content().string(containsString("ReleaseFlow")))
                .andReturn();

        UUID entryId = onlyEntryId();
        assertThat(page.getResponse().getContentAsString()).contains("entry-" + entryId);

        mockMvc.perform(get("/changelog/{slug}/releases/{entryId}", slug, entryId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("ETag", "\"" + entryId + "\""))
                .andExpect(content().string(containsString("add the inbox")));

        // A reader who already has the entry is told nothing changed.
        mockMvc.perform(get("/changelog/{slug}/releases/{entryId}", slug, entryId)
                        .header("If-None-Match", "\"" + entryId + "\""))
                .andExpect(status().isNotModified());

        mockMvc.perform(get("/changelog/{slug}/rss.xml", slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/rss+xml")))
                .andExpect(content().string(containsString("<rss version=\"2.0\">")))
                .andExpect(content().string(containsString("<guid isPermaLink=\"true\">")))
                .andExpect(content().string(containsString("/changelog/" + slug + "/releases/" + entryId)));

        // The run says where the note ended up.
        mockMvc.perform(get("/api/automation/runs").session(owner.session()))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].actions[0].externalReference")
                        .value(containsString("/changelog/" + slug + "/releases/" + entryId)));
    }

    @Test
    void readsOnlyAndHandsOutNoCookie() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        String slug = slug(owner);

        // Nothing here is excused from CSRF, and nothing needs to be: a write is refused
        // whether or not a token comes with it, and looking never earns a session.
        mockMvc.perform(get("/changelog/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(post("/changelog/{slug}", slug))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(post("/changelog/{slug}", slug).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void neverShowsAnotherOrganizationsReleasesOrAnAddressNobodyAnswers() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID ruleId = createdRuleId(createRule(
                owner, changelogRule("Publish", projectId, audienceId(owner, "end_user")))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        publishedRelease(owner, projectId, "1.4.0");
        deliverEverything();

        Owner other = registerAndLogin("other@example.com", "Linh Pham");
        String otherSlug = slug(other);

        mockMvc.perform(get("/changelog/{slug}", otherSlug))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing has been published yet.")))
                .andExpect(content().string(not(containsString("1.4.0"))));
        mockMvc.perform(get("/changelog/{slug}", "nobody-here")).andExpect(status().isNotFound());
        mockMvc.perform(get("/changelog/{slug}/releases/{entryId}", otherSlug, onlyEntryId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void escapesWhateverAPersonWroteInTheNote() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, changelogRule("Publish", projectId, endUser))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());

        UUID releaseId = approvedReleaseWithChange(owner, projectId, "1.4.0");
        writeNote(owner, projectId, releaseId, endUser,
                "<script>alert('x')</script>\n\n[click](javascript:alert(1))\n\n<img src=x onerror=alert(1)>");
        publish(owner, projectId, releaseId);
        deliverEverything();

        String slug = slug(owner);
        String entry = mockMvc.perform(get("/changelog/{slug}/releases/{entryId}", slug, onlyEntryId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Whatever the note said is text, not markup: the page has its own scripts, and
        // none of them came from a release note.
        assertThat(entry).doesNotContain("<script>alert", "href=\"javascript:", "<img src=x");
        assertThat(entry).contains("&lt;script&gt;alert", "&lt;img src=x onerror=alert(1)&gt;");
        // A link to a scheme nobody should follow keeps its text and loses its target.
        assertThat(entry).contains("<a rel=\"nofollow noopener noreferrer\" href=\"\">click</a>");

        String feed = mockMvc.perform(get("/changelog/{slug}/rss.xml", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(feed).doesNotContain("<script>", "href=\"javascript:", "<img src=x");
    }

    @Test
    void neverRewritesWhatSomebodyMayAlreadyHaveRead() throws Exception {
        Owner owner = registerAndLogin("owner@example.com", "Mai Tran");
        UUID projectId = createProject(owner);
        UUID endUser = audienceId(owner, "end_user");
        UUID ruleId = createdRuleId(createRule(owner, changelogRule("Publish", projectId, endUser))
                .andExpect(status().isCreated()));
        enable(owner, ruleId).andExpect(status().isOk());
        publishedRelease(owner, projectId, "1.4.0");
        deliverEverything();

        UUID entryId = onlyEntryId();

        // The database refuses to let anything change or remove a published entry.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public_changelog_entries SET content_snapshot = 'rewritten' WHERE id = ?", entryId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM public_changelog_entries WHERE id = ?", entryId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A second delivery of the same note is the entry that is already there.
        UUID secondRule = createdRuleId(createRule(owner, byHandChangelogRule("Publish again", projectId, endUser))
                .andExpect(status().isCreated()));
        enable(owner, secondRule).andExpect(status().isOk());
        mockMvc.perform(post("/api/automation/rules/{ruleId}/execute", secondRule)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseId\":\"%s\",\"requestId\":\"%s\"}"
                                .formatted(releaseId(owner), UUID.randomUUID())))
                .andExpect(status().isAccepted());
        deliverEverything();

        assertThat(entryCount()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_action_runs WHERE status = 'SUCCEEDED'", Integer.class))
                .isEqualTo(2);
    }

    private String changelogRule(String name, UUID projectId, UUID audienceId) {
        return """
                {"name":"%s","triggerType":"RELEASE_PUBLISHED","projectId":"%s",
                 "actions":[{"actionType":"PUBLIC_CHANGELOG","audienceId":"%s","language":"en"}]}
                """.formatted(name, projectId, audienceId);
    }

    private String byHandChangelogRule(String name, UUID projectId, UUID audienceId) {
        return changelogRule(name, projectId, audienceId).replace("RELEASE_PUBLISHED", "MANUAL");
    }

    private String slug(Owner owner) throws Exception {
        String body = mockMvc.perform(get("/api/organization/slug").session(owner.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slug");
    }

    private UUID onlyEntryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public_changelog_entries ORDER BY published_at DESC LIMIT 1", UUID.class);
    }

    private UUID releaseId(Owner owner) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM releases WHERE organization_id = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, owner.organizationId());
    }

    private Integer entryCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM public_changelog_entries", Integer.class);
    }

    private void writeNote(Owner owner, UUID projectId, UUID releaseId, UUID audienceId, String content)
            throws Exception {
        UUID noteId = jdbcTemplate.queryForObject(
                "SELECT id FROM release_audience_notes WHERE release_id = ? AND audience_id = ?",
                UUID.class, releaseId, audienceId);
        mockMvc.perform(put("/api/projects/{projectId}/releases/{releaseId}/notes/{noteId}",
                        projectId, releaseId, noteId)
                        .session(owner.session())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectContent(content)))
                .andExpect(status().isOk());
    }

    private static String objectContent(String content) {
        return "{\"content\":\"%s\"}".formatted(content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n"));
    }

    private void publish(Owner owner, UUID projectId, UUID releaseId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/releases/{releaseId}/publish", projectId, releaseId)
                        .session(owner.session())
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
