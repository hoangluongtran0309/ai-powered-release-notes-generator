package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UiLocaleIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String COOKIE = "releaseflow_lang";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @BeforeEach
    @AfterEach
    void clearDatabase() {
        appUserRepository.deleteAll();
        deleteOrganizationSettings();
        organizationRepository.deleteAll();
    }

    @Test
    void theCookieOutranksTheAccountWhichOutranksTheBrowser() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");

        // 4. Nothing asks, so the first configured language answers.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Workspace overview")));

        // 3. Accept-Language, narrowed from a region to a language this deployment ships.
        mockMvc.perform(get("/").session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Tổng quan không gian làm việc")));

        // A language it does not ship falls through to the configured one.
        mockMvc.perform(get("/").session(session).header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Workspace overview")));

        // 2. The account's own choice, for a session that starts with no cookie.
        mockMvc.perform(put("/api/me/ui-locale")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiLocale\":\"vi-VN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiLocale").value("vi"))
                .andExpect(cookie().value(COOKIE, "vi"))
                .andExpect(cookie().httpOnly(COOKIE, true));
        assertThat(appUserRepository.findByEmail("owner@example.com").orElseThrow().getUiLocale()).isEqualTo("vi");

        MockHttpSession freshSession = login("owner@example.com", "owner-password");
        mockMvc.perform(get("/").session(freshSession))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Tổng quan không gian làm việc")));

        // 1. The cookie, even against an account that says otherwise.
        mockMvc.perform(get("/").session(freshSession).cookie(new Cookie(COOKIE, "en")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Workspace overview")));

        // A cookie naming a language this deployment does not ship is ignored.
        mockMvc.perform(get("/").session(freshSession).cookie(new Cookie(COOKIE, "fr")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Tổng quan không gian làm việc")));
    }

    @Test
    void followingTheBrowserAgainClearsTheColumnAndTheCookie() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");
        mockMvc.perform(put("/api/me/ui-locale").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uiLocale\":\"vi\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/me/ui-locale").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uiLocale\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiLocale").doesNotExist())
                .andExpect(cookie().maxAge(COOKIE, 0));

        assertThat(appUserRepository.findByEmail("owner@example.com").orElseThrow().getUiLocale()).isNull();
    }

    @Test
    void aLanguageThisDeploymentDoesNotShipIsRefusedWithAStableCode() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");

        mockMvc.perform(put("/api/me/ui-locale").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uiLocale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ui_locale_invalid"))
                .andExpect(jsonPath("$.title").value("Invalid interface language"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("\"fr\"")));

        mockMvc.perform(put("/api/me/ui-locale").session(session).with(csrf())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uiLocale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ui_locale_invalid"))
                .andExpect(jsonPath("$.title").value("Ngôn ngữ giao diện không hợp lệ"));

        // Too long for the column, so the request is refused before the service sees it.
        mockMvc.perform(put("/api/me/ui-locale").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uiLocale\":\"aaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(appUserRepository.findByEmail("owner@example.com").orElseThrow().getUiLocale()).isNull();
    }

    @Test
    void thePickerInTheUserMenuSavesTheChoiceAndReturnsToThePage() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");

        mockMvc.perform(post("/settings/ui-locale")
                        .session(session)
                        .with(csrf())
                        .header(HttpHeaders.REFERER, "http://localhost/projects?project=1")
                        .param("uiLocale", "vi"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/projects"))
                .andExpect(cookie().value(COOKIE, "vi"));
        assertThat(appUserRepository.findByEmail("owner@example.com").orElseThrow().getUiLocale()).isEqualTo("vi");

        // A Referer pointing somewhere else only ever sends the person back to the workspace.
        mockMvc.perform(post("/settings/ui-locale")
                        .session(session)
                        .with(csrf())
                        .header(HttpHeaders.REFERER, "https://elsewhere.example/projects")
                        .param("uiLocale", "en"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/projects"));
    }

    @Test
    void anErrorAndTheFormItCameFromAreBothInTheReadersLanguage() throws Exception {
        registerAndLogin("owner@example.com", "owner-password");

        // A validation failure on the registration form, in Vietnamese.
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .cookie(new Cookie(COOKIE, "vi"))
                        .param("organizationName", "")
                        .param("displayName", "Owner")
                        .param("email", "not-an-email")
                        .param("password", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Cần có tên Organization.")))
                .andExpect(content().string(Matchers.containsString("Hãy nhập một địa chỉ email hợp lệ.")))
                .andExpect(content().string(Matchers.containsString("Mật khẩu phải có ít nhất 12 ký tự")));

        // The same failure as Problem Details, with its code unchanged.
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .cookie(new Cookie(COOKIE, "vi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Acme","displayName":"Owner",
                                 "email":"owner@example.com","password":"another-password"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"))
                .andExpect(jsonPath("$.title").value("Email đã được đăng ký"))
                .andExpect(jsonPath("$.detail").value("Đã có tài khoản dùng địa chỉ email này."));
    }

    @Test
    void anUnauthenticatedApiCallIsRefusedInTheReadersLanguage() throws Exception {
        mockMvc.perform(get("/api/me/ui-locale").cookie(new Cookie(COOKIE, "vi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.title").value("Cần đăng nhập"))
                .andExpect(jsonPath("$.detail").value("Hãy đăng nhập để dùng tài nguyên này."));

        mockMvc.perform(get("/api/me/ui-locale"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void theChangelogChromeFollowsTheReaderWithoutCreatingASession() throws Exception {
        MockHttpSession session = registerAndLogin("owner@example.com", "owner-password");
        mockMvc.perform(post("/organization/slug").session(session).with(csrf()).param("slug", "acme"))
                .andExpect(status().isFound());

        MvcResult result = mockMvc.perform(get("/changelog/acme")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Chưa có gì được publish.")))
                .andExpect(content().string(Matchers.containsString("Được phát hành bằng ReleaseFlow.")))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).as("anonymous reading stays anonymous").isNull();
    }

    private MockHttpSession registerAndLogin(String email, String password) throws Exception {
        RegistrationRequest request = new RegistrationRequest();
        request.setOrganizationName("Acme");
        request.setDisplayName("Owner");
        request.setEmail(email);
        request.setPassword(password);
        registrationService.register(request);
        return login(email, password);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isFound())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
