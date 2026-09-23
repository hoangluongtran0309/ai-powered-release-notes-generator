package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.support.GitHubStub;
import com.hoangluongtran0309.releaseflow.support.JiraStub;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the Project's Jira project adds to a pull request that mentions its issues. */
class LinkedContextIntegrationTest extends PostgreSqlIntegrationTest {

    private static final GitHubStub GITHUB = GitHubStub.start();
    private static final JiraStub JIRA = JiraStub.start();
    private static final OpenAiStub OPENAI = OpenAiStub.start();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String COMMIT_SECRET = "commit-only-detail-never-stored";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeProcessingWorker worker;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("releaseflow.github.api-base-url", GITHUB::baseUrl);
        registry.add("releaseflow.github.timeout", () -> "PT2S");
        registry.add("releaseflow.jira.api-base-url", JIRA::baseUrl);
        registry.add("releaseflow.jira.timeout", () -> "PT2S");
        registry.add("releaseflow.ai.provider", () -> "openai");
        registry.add("releaseflow.ai.timeout", () -> "PT2S");
        registry.add("releaseflow.openai.base-url", OPENAI::baseUrl);
        registry.add("releaseflow.openai.api-key", () -> "sk-linked-test");
        registry.add("releaseflow.openai.model", () -> "gpt-test");
    }

    @AfterAll
    static void stopStubs() {
        GITHUB.close();
        JIRA.close();
        OPENAI.close();
    }

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        GITHUB.reset();
        JIRA.reset();
        OPENAI.reset();
        OPENAI.respondWithClassification("FEATURE", false, "Adds CSV export.");
        jdbcTemplate.update("DELETE FROM change_processing_jobs");
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM source_sync_jobs");
        jdbcTemplate.update("DELETE FROM integration_sources");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        deleteOrganizationSettings();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void aProjectWithoutJiraLooksNothingUp() throws Exception {
        Project project = connect("plain@example.com", false);
        GITHUB.respondWithFiles("src/main/java/App.java");

        deliver(project, 1, "feat: export (APP-7)", null);
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.NOT_CONFIGURED);
        assertThat(change.getLinkedIssues()).isEmpty();
        assertThat(GITHUB.commitRequests()).as("commits are read only to find issue keys").isEmpty();
        assertThat(JIRA.requests()).isEmpty();
    }

    @Test
    void collectsTheIssuesTheTitleDescriptionAndCommitsMention() throws Exception {
        Project project = connect("collected@example.com", true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.respondWithCommits("wip " + COMMIT_SECRET, "Refs APP-8 and OPS-1", "tidy app-7");
        JIRA.respondWithIssue("APP-7", "Export tables", "Story", "Done", "Users asked for CSV.");
        JIRA.respondWithIssue("APP-8", "Stream rows", "Task", "In Review", "Avoid loading the table.");
        JIRA.respondWithIssue("APP-9", "Document export", "Task", "Done", "");

        deliver(project, 2, "feat: export (APP-7)", "Also covers APP-9.");
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.COLLECTED);
        assertThat(change.getLinkedIssues()).extracting(LinkedIssue::key).containsExactly("APP-7", "APP-9", "APP-8");
        assertThat(change.getLinkedIssues().getFirst()).isEqualTo(new LinkedIssue(
                "APP-7", "Export tables", "Users asked for CSV.", "Story", "Done",
                JiraStub.SITE + "/browse/APP-7"));
        assertThat(change.getReviewTriggers()).extracting(ReviewTrigger::type)
                .doesNotContain(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE);
        assertThat(JIRA.issueRequests()).allSatisfy(request -> assertThat(request.authorization())
                .startsWith("Basic "));

        // Commit messages are evidence only: they are read, never kept.
        assertThat(jdbcTemplate.queryForObject("SELECT row_to_json(changes)::text FROM changes", String.class))
                .doesNotContain(COMMIT_SECRET);

        JsonNode prompt = userPrompt();
        assertThat(prompt.path("linked_issues").values())
                .extracting(issue -> issue.path("key").asString())
                .containsExactly("APP-7", "APP-9", "APP-8");
        assertThat(prompt.path("linked_issues").get(0).path("description").asString()).isEqualTo("Users asked for CSV.");
        assertThat(prompt.toString()).doesNotContain(COMMIT_SECRET);

        mockMvc.perform(get("/changes").param("project", project.projectId().toString()).session(project.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Linked issues (3)")))
                .andExpect(content().string(containsString(JiraStub.SITE + "/browse/APP-8")))
                .andExpect(content().string(containsString("Stream rows")))
                .andExpect(content().string(not(containsString("Linked issues incomplete"))));
        mockMvc.perform(get("/api/projects/{projectId}/changes", project.projectId()).session(project.session()))
                .andExpect(jsonPath("$[0].linkedContextStatus").value("COLLECTED"))
                .andExpect(jsonPath("$[0].linkedIssues[1].key").value("APP-9"));
    }

    @Test
    void readCommitsThatNameNoIssueFindNothing() throws Exception {
        Project project = connect("notfound@example.com", true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.respondWithCommits("tidy the exporter");

        deliver(project, 3, "feat: export", null);
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.NOT_FOUND);
        assertThat(change.getReviewTriggers()).extracting(ReviewTrigger::type)
                .doesNotContain(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE);
        assertThat(JIRA.issueRequests()).isEmpty();
        assertThat(userPrompt().path("linked_issues").isEmpty()).isTrue();
    }

    @Test
    void unreadableCommitsAreRetriedThenSettleAsPartial() throws Exception {
        Project project = connect("partial@example.com", true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.failCommits(500);
        JIRA.respondWithIssue("APP-7", "Export tables", "Story", "Done", "");

        deliver(project, 4, "feat: export (APP-7)", null);
        worker.processOne();

        Change pending = onlyChange();
        assertThat(pending.getProcessingStatus()).as("the change stays visibly in progress")
                .isEqualTo(ProcessingStatus.PROCESSING);
        mockMvc.perform(get("/changes").param("project", project.projectId().toString()).session(project.session()))
                .andExpect(content().string(containsString("Processing")));

        for (int attempt = 2; attempt <= ChangeProcessingWorker.MAX_ATTEMPTS; attempt++) {
            makeDue();
            worker.processOne();
        }

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        // A key may hide in a commit nobody read, so the issues found so far are kept and flagged.
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.PARTIAL);
        assertThat(change.getLinkedIssues()).extracting(LinkedIssue::key).containsExactly("APP-7");
        assertThat(change.getReviewTriggers()).extracting(ReviewTrigger::type)
                .contains(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE);
        assertThat(change.isNeedsReview()).isTrue();
        mockMvc.perform(get("/changes").param("project", project.projectId().toString()).session(project.session()))
                .andExpect(content().string(containsString("Linked issues incomplete")))
                .andExpect(content().string(containsString("Commit messages could not be read")));
    }

    @Test
    void anIssueJiraWillNotShowSettlesAsUnavailable() throws Exception {
        Project project = connect("unavailable@example.com", true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.respondWithCommits("feat: APP-7 and APP-404");
        JIRA.respondWithIssue("APP-7", "Export tables", "Story", "Done", "");

        deliver(project, 5, "feat: export", null);
        for (int attempt = 1; attempt <= ChangeProcessingWorker.MAX_ATTEMPTS; attempt++) {
            makeDue();
            worker.processOne();
        }

        Change change = onlyChange();
        assertThat(change.getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.UNAVAILABLE);
        assertThat(change.getLinkedIssues()).extracting(LinkedIssue::key).containsExactly("APP-7");
        assertThat(change.getReviewTriggers())
                .contains(new ReviewTrigger(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE, "UNAVAILABLE"));
        assertThat(JIRA.issueRequests()).filteredOn(request -> request.path().endsWith("/APP-404"))
                .hasSize(ChangeProcessingWorker.MAX_ATTEMPTS);
        mockMvc.perform(get("/changes").param("project", project.projectId().toString()).session(project.session()))
                .andExpect(content().string(containsString("Jira did not return every issue")));
    }

    @Test
    void aTransientJiraFailureRecoversOnRetry() throws Exception {
        Project project = connect("recovers@example.com", true);
        GITHUB.respondWithFiles("src/main/java/Export.java");
        GITHUB.respondWithCommits("fix APP-7");
        JIRA.failIssue("APP-7");

        deliver(project, 6, "fix: export", null);
        worker.processOne();
        assertThat(onlyChange().getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSING);

        JIRA.reset();
        JIRA.respondWithIssue("APP-7", "Export tables", "Bug", "Done", "");
        makeDue();
        worker.processOne();

        Change change = onlyChange();
        assertThat(change.getLinkedContextStatus()).isEqualTo(LinkedContextStatus.COLLECTED);
        assertThat(change.getReviewTriggers()).extracting(ReviewTrigger::type)
                .doesNotContain(ReviewTriggerType.LINKED_CONTEXT_UNAVAILABLE);
    }

    private JsonNode userPrompt() {
        List<OpenAiStub.RecordedRequest> requests = OPENAI.requests();
        assertThat(requests).isNotEmpty();
        JsonNode body = OBJECT_MAPPER.readTree(requests.getLast().body());
        for (JsonNode message : body.path("messages").values()) {
            if ("user".equals(message.path("role").asString())) {
                return OBJECT_MAPPER.readTree(message.path("content").asString());
            }
        }
        throw new AssertionError("The AI request had no user message: " + body);
    }

    private void makeDue() {
        jdbcTemplate.update("UPDATE change_processing_jobs SET next_attempt_at = now() - interval '1 second'");
    }

    private Change onlyChange() {
        List<Change> changes = changeRepository.findAll();
        assertThat(changes).hasSize(1);
        return changes.getFirst();
    }

    private void deliver(Project project, int number, String title, String description) throws Exception {
        String body = """
                {"action":"closed","number":%d,"pull_request":{"number":%d,"title":"%s","body":%s,\
                "merged":true,"merged_at":"2026-09-10T09:14:22Z",\
                "merge_commit_sha":"0123456789abcdef0123456789abcdef01234567",\
                "html_url":"https://github.com/acme/releaseflow/pull/%d","user":{"login":"mai-dev"},\
                "base":{"ref":"main"},"labels":[]},"repository":{"full_name":"acme/releaseflow"}}"""
                .formatted(number, number, title, description == null ? "null" : "\"" + description + "\"", number);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(project.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + java.util.HexFormat.of()
                .formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(project.webhookPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                        .header("X-Hub-Signature-256", signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("recorded"));
    }

    private Project connect(String email, boolean withJira) throws Exception {
        RegistrationRequest registration = new RegistrationRequest();
        registration.setOrganizationName(email);
        registration.setDisplayName("Owner");
        registration.setEmail(email);
        registration.setPassword("owner-password");
        registrationService.register(registration);
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", "owner-password"))
                .andExpect(status().isFound())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult project = mockMvc.perform(post("/api/projects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ReleaseFlow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));

        MvcResult repository = mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"acme\",\"repository\":\"releaseflow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String created = repository.getResponse().getContentAsString();
        mockMvc.perform(put("/api/projects/{projectId}/sources/{sourceId}/token", projectId,
                        JsonPath.read(created, "$.id"))
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"github_pat_linked-test\"}"))
                .andExpect(status().isNoContent());
        if (withJira) {
            mockMvc.perform(post("/api/projects/{projectId}/sources", projectId)
                            .session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"JIRA","siteUrl":"%s","projectKey":"APP",\
                                    "accountEmail":"bot@acme.test","apiToken":"jira-linked-token"}"""
                                    .formatted(JiraStub.SITE)))
                    .andExpect(status().isCreated());
        }
        GITHUB.reset();
        JIRA.reset();
        return new Project(projectId, JsonPath.read(created, "$.webhookPath"), JsonPath.read(created, "$.webhookSecret"),
                session);
    }

    private record Project(UUID projectId, String webhookPath, String secret, MockHttpSession session) {
    }
}
