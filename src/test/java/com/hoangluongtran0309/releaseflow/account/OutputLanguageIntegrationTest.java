package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutputLanguageIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM app_users");
        deleteAudiences();
        jdbcTemplate.update("DELETE FROM organizations");
    }

    @Test
    void registrationStoresACanonicalOutputLanguage() throws Exception {
        MvcResult registered = register("owner@example.com", "vi_VN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outputLanguage").value("vi-VN"))
                .andReturn();
        UUID organizationId = UUID.fromString(JsonPath.read(registered.getResponse().getContentAsString(), "$.organizationId"));
        assertThat(organizationRepository.findById(organizationId).orElseThrow().getOutputLanguage().tag())
                .isEqualTo("vi-VN");

        register("other@example.com", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outputLanguage").value("en"));
        register("third@example.com", "xx")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("output_language_invalid"));
        assertThat(organizationRepository.count()).isEqualTo(2);
    }

    @Test
    void administratorsChangeTheLanguageAndMembersOnlyReadIt() throws Exception {
        register("owner@example.com", null).andExpect(status().isCreated());
        MockHttpSession owner = login("owner@example.com", "owner-password");
        UUID organizationId = organizationRepository.findAll().getFirst().getId();
        MockHttpSession member = member(organizationId, "member@example.com");

        mockMvc.perform(get("/api/organization/output-language").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outputLanguage").value("en"))
                .andExpect(jsonPath("$.displayName").value("English"))
                .andExpect(jsonPath("$.supported[1].tag").value("vi"))
                .andExpect(jsonPath("$.supported[1].displayName").value("Vietnamese"));

        mockMvc.perform(changeLanguage(member, "vi")).andExpect(status().isForbidden());
        mockMvc.perform(changeLanguage(owner, "pt_br"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outputLanguage").value("pt-BR"))
                .andExpect(jsonPath("$.displayName").value("Portuguese (Brazil)"));
        mockMvc.perform(changeLanguage(owner, "not-a-language"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("output_language_invalid"));
        mockMvc.perform(changeLanguage(owner, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(organizationRepository.findById(organizationId).orElseThrow().getOutputLanguage().tag())
                .isEqualTo("pt-BR");
    }

    @Test
    void theProjectsPageShowsAndChangesTheLanguage() throws Exception {
        register("owner@example.com", null).andExpect(status().isCreated());
        MockHttpSession owner = login("owner@example.com", "owner-password");
        MockHttpSession member = member(organizationRepository.findAll().getFirst().getId(), "member@example.com");

        mockMvc.perform(get("/projects").session(owner))
                .andExpect(content().string(containsString("English (en)")))
                .andExpect(content().string(containsString("Save language")));
        mockMvc.perform(get("/projects").session(member))
                .andExpect(content().string(containsString("English (en)")))
                .andExpect(content().string(not(containsString("Save language"))));

        mockMvc.perform(post("/organization/output-language").session(owner).with(csrf()).param("outputLanguage", "vi"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/projects?languageSaved"));
        mockMvc.perform(get("/projects").param("languageSaved", "").session(owner))
                .andExpect(content().string(containsString("Output language saved")))
                .andExpect(content().string(containsString("Vietnamese (vi)")));

        mockMvc.perform(post("/organization/output-language").session(owner).with(csrf()).param("outputLanguage", "xx"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("is not a valid language tag")));
        mockMvc.perform(post("/organization/output-language").session(member).with(csrf()).param("outputLanguage", "en"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theRegistrationFormOffersLanguagesAndReportsInvalidTags() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(content().string(containsString("list=\"output-language-options\"")))
                .andExpect(content().string(containsString("value=\"vi\"")));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("organizationName", "Acme")
                        .param("displayName", "Owner")
                        .param("email", "owner@example.com")
                        .param("password", "owner-password")
                        .param("outputLanguage", "klingon"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("is not a valid language tag")));
        assertThat(organizationRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String outputLanguage) throws Exception {
        String language = outputLanguage == null ? "" : ",\"outputLanguage\":\"" + outputLanguage + "\"";
        return mockMvc.perform(post("/api/registrations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationName\":\"Acme\",\"displayName\":\"Owner\",\"email\":\"%s\",\"password\":\"owner-password\"%s}"
                        .formatted(email, language)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder changeLanguage(
            MockHttpSession session,
            String language
    ) {
        return put("/api/organization/output-language")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outputLanguage\":\"%s\"}".formatted(language));
    }

    private MockHttpSession member(UUID organizationId, String email) throws Exception {
        jdbcTemplate.update(
                """
                        INSERT INTO app_users (id, organization_id, email, password_hash, display_name, role, created_at)
                        VALUES (?, ?, ?, ?, 'Member', 'MEMBER', now())
                        """,
                UUID.randomUUID(),
                organizationId,
                email,
                passwordEncoder.encode("member-password")
        );
        return login(email, "member-password");
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
