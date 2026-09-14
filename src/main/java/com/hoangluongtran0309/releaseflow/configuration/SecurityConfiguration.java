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

    // GitHub deliveries carry no session or CSRF token; the per-integration HMAC
    // signature is verified by the webhook controller instead.
    @Bean
    @Order(1)
    SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/webhooks/github/**")
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
                        // Configuring a repository creates its webhook signing secret.
                        .requestMatchers(HttpMethod.POST, "/projects/*/github-integration", "/api/projects/*/github-integration")
                        .hasRole("ADMIN")
                        // An access token is a credential, set only by administrators.
                        .requestMatchers(HttpMethod.POST, "/projects/*/github-integration/token")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/projects/*/github-integration/token")
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
