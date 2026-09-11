package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.RegistrationRequest;
import com.hoangluongtran0309.releaseflow.account.RegistrationService;
import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"releaseflow.openai.api-key=", "releaseflow.openai.model="})
class ChangeAiDisabledIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private ChangeRepository changeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM changes");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM app_users");
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void hidesTheAiActionAndReportsItAsUnavailable() throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Owner");
        request.setDisplayName("Owner");
        request.setEmail("owner@example.com");
        request.setPassword("owner-password");
        UUID organizationId = registrationService.register(request).organizationId();
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "owner@example.com")
                        .param("password", "owner-password"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ReleaseFlow\"}"))
                .andReturn();
        UUID projectId = UUID.fromString(JsonPath.read(project.getResponse().getContentAsString(), "$.id"));
        MergedPullRequest pullRequest = new MergedPullRequest(
                1, "Tidy the exporter", null, "mai-dev", List.of(), "main",
                "0123456789abcdef0123456789abcdef01234567", Instant.parse("2026-09-01T10:00:00Z"),
                "https://github.com/acme/releaseflow/pull/1"
        );
        UUID changeId = UUID.randomUUID();
        changeRepository.saveAndFlush(new Change(
                changeId, organizationId, projectId, pullRequest,
                ChangeClassifier.classify(pullRequest), UUID.randomUUID(), Instant.now()
        ));

        mockMvc.perform(get("/changes").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RELEASEFLOW_OPENAI_API_KEY")))
                .andExpect(content().string(not(containsString("Classify with AI"))));
        mockMvc.perform(post("/api/projects/{projectId}/changes/{changeId}/ai-classification", projectId, changeId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ai_classification_unavailable"));
        mockMvc.perform(post("/projects/{projectId}/changes/{changeId}/ai-classification", projectId, changeId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("AI classification is not configured.")));
    }
}
