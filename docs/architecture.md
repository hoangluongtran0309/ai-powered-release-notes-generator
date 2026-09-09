# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, and shared `configuration`:

```text
GET  /                         -> Thymeleaf home
GET  /register                -> owner registration form
POST /register                -> RegistrationService
POST /api/registrations       -> RegistrationService
POST /login                   -> Spring Security authentication
POST /logout                  -> Spring Security logout
GET  /api/session             -> authenticated principal identity
GET  /api/csrf                -> CSRF token for session-based REST clients
GET  /api/status              -> JSON status
```

REST and Thymeleaf registration call the same transactional application
service. One short transaction creates an Organization and its OWNER AppUser.
Passwords are encoded with BCrypt before persistence; neither the hash nor the
submitted password is returned.

## Persistence and tenant boundary

PostgreSQL is the only supported database. Flyway owns the schema and starts at
`V1`; Hibernate uses `validate` and never creates or updates tables. Integration
tests use PostgreSQL 17 through Testcontainers and run the same migration.

```text
organizations
  id (UUID PK)
  name
  created_at

app_users
  id (UUID PK)
  organization_id (FK -> organizations.id)
  email (canonical, globally unique)
  password_hash
  display_name
  role (OWNER)
  created_at
```

Authenticated `ReleaseFlowPrincipal` contains both user ID and Organization ID.
Request DTOs do not accept tenant identifiers. Tenant-owned repositories added
in future slices must query using both the resource ID and the principal's
Organization ID. This decision is recorded in
[ADR-0001](adr/0001-shared-schema-tenant-isolation.md).

## HTTP security and errors

Spring Security uses server-side sessions. CSRF remains enabled for REST and UI
writes, including registration, login, and logout. Thymeleaf inserts hidden
tokens into forms; REST clients obtain a token from `GET /api/csrf`. Anonymous
API requests receive a 401 problem response instead of an HTML redirect.

Current REST failures use `application/problem+json` and a stable `code`, while
UI validation displays the same application errors next to the relevant field.
Session cookies are HttpOnly and SameSite=Lax; deployments using HTTPS must set
the secure-cookie environment switch.

## Development direction

New code is grouped by product capability. A capability starts with direct,
readable classes and gains internal layers only when implemented behavior needs
them. There is no external service call, background worker, or separately
deployed frontend in the current system.
