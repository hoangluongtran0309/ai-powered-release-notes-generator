package com.hoangluongtran0309.releaseflow.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Clock;

@Configuration
public class SecurityConfiguration {

    /**
     * Deliveries from a provider, and calls to an Organization's own automation webhook.
     * Each one is proven by a signature over the request itself, and none of them reads
     * a session or a cookie, so a cross-site request has no ambient authority to borrow
     * and a CSRF token is something the callers could never send.
     *
     * <p>The exemption is therefore named rather than switched off: protection stays
     * configured and only these paths are excused, so a path added here later has to be
     * excused deliberately. Only the five endpoints that exist are reachable at all;
     * anything else under {@code /webhooks/} is refused rather than published by accident.
     */
    @Bean
    @Order(1)
    SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/webhooks/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/webhooks/github/*",
                                "/webhooks/gitlab/*",
                                "/webhooks/linear/*",
                                "/webhooks/automation/*"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/webhooks/automation/*/runs/*").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"))
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * A public changelog is read by people who have no account and must never be given a
     * session for looking: no request cache, no cookie, nothing but what was published.
     *
     * <p>Nothing here is excused from CSRF, and nothing needs to be: only GET is allowed,
     * and a token is required of exactly the methods this chain already refuses.
     */
    @Bean
    @Order(2)
    SecurityFilterChain publicChangelogSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/changelog/**")
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.GET, "/changelog/**")
                        .permitAll()
                        .anyRequest().denyAll())
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/register",
                                "/login",
                                "/api/status",
                                "/api/csrf",
                                "/api/registrations",
                                "/css/**",
                                "/js/**",
                                "/svg/**",
                                "/webjars/**",
                                "/favicon.ico",
                                "/error",
                                "/accept-invite",
                                "/accept-invite/**",
                                "/api/public/invitations/**"
                        ).permitAll()
                        .requestMatchers("/members", "/members/**", "/api/members", "/api/invitations/**")
                        .hasRole("ADMIN")
                        // Audiences shape every release note the Organization writes.
                        .requestMatchers("/audiences", "/audiences/**", "/api/audiences", "/api/audiences/**")
                        .hasRole("ADMIN")
                        // Members read the category catalog; only administrators change it or
                        // decide what the AI proposes.
                        .requestMatchers(HttpMethod.GET, "/api/categories").authenticated()
                        .requestMatchers("/categories", "/categories/**", "/api/categories", "/api/categories/**",
                                "/api/category-suggestions", "/api/category-suggestions/**")
                        .hasRole("ADMIN")
                        // Connecting a source creates its webhook signing secret; an access token is a
                        // credential; an import reads a repository's history. All are administrator only.
                        .requestMatchers(HttpMethod.POST, "/projects/*/sources", "/api/projects/*/sources")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/projects/*/sources/*/token")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/projects/*/sources/*/token")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/projects/*/sources/*/imports", "/projects/*/sources/*/imports/resume",
                                "/api/projects/*/sources/*/imports", "/api/projects/*/sources/*/imports/resume")
                        .hasRole("ADMIN")
                        // Members read a Project's sensitive paths; administrators add to them.
                        .requestMatchers(HttpMethod.PUT, "/api/projects/*/sensitive-paths")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/projects/*/sensitive-paths")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/organization/release-languages")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/organization/output-language")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/organization/output-language")
                        .hasRole("ADMIN")
                        // Moving the public changelog breaks every link already shared.
                        .requestMatchers(HttpMethod.PUT, "/api/organization/slug")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/organization/slug")
                        .hasRole("ADMIN")
                        // An automation rule delivers release notes outside ReleaseFlow and holds
                        // the credentials to do it.
                        .requestMatchers("/automation", "/automation/**", "/api/automation", "/api/automation/**")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                authenticationEntryPoint,
                                PathPatternRequestMatcher.pathPattern("/api/**")
                        )
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
