# Changelog

All notable changes to ReleaseFlow are documented in this file. No public
version has been released.

## [Unreleased]

### Added

- A single-module Java 21 and Spring Boot 4.1.1 application baseline.
- A JSON application status endpoint at `GET /api/status`.
- A minimal server-rendered home page at `GET /`.
- Startup, REST, and Thymeleaf smoke tests.
- Architecture and implementation-status documentation for the runnable
  baseline.
- Atomic Organization owner registration through REST and Thymeleaf.
- PostgreSQL persistence with Flyway migration `V1` for organizations and app
  users.
- Canonical unique email enforcement and BCrypt password hashing.
- Session authentication, custom form login, CSRF-protected REST/UI writes,
  and POST logout.
- Authenticated `GET /api/session` and public `GET /api/csrf` endpoints.
- Stable `application/problem+json` errors for registration and authentication
  failures.
- PostgreSQL Testcontainers coverage for migrations, constraints, registration,
  authentication, CSRF, tenant identity, and UI paths.
