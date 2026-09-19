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

    // Provider deliveries carry no session or CSRF token; each provider's own verifier
    // establishes trust from the source's secret before anything is read.
    @Bean
    @Order(1)
    SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/webhooks/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
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
