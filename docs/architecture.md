# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, `project`, and shared `configuration`:

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
GET  /projects                -> Project and GitHub configuration UI
POST /projects                -> ProjectService
POST /projects/{id}/github-integration -> GitHubIntegrationService
GET  /api/projects            -> tenant-scoped Project list
POST /api/projects            -> ProjectService
POST /api/projects/{id}/github-integration -> GitHubIntegrationService
```

REST and Thymeleaf registration call the same transactional application
service. Project REST and UI controllers likewise call the same Project and
GitHub integration services. One short registration transaction creates an
Organization and its OWNER AppUser.
Passwords are encoded with BCrypt before persistence; neither the hash nor the
submitted password is returned.

## Persistence and tenant boundary

PostgreSQL is the only supported database. Flyway owns the schema and starts at
`V1`; Hibernate uses `validate` and never creates or updates tables. Integration
tests use PostgreSQL 17 through Testcontainers and run the same migrations.

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

projects
  id (UUID PK)
  organization_id (FK -> organizations.id)
  name
  created_at

github_integrations
  id (UUID PK)
  organization_id
  project_id (composite FK with organization_id -> projects)
  repository_owner + repository_name (unique per Organization)
  webhook_id (globally unique)
  secret_nonce + secret_ciphertext
  created_at
```

Authenticated `ReleaseFlowPrincipal` contains both user ID and Organization ID.
Request DTOs do not accept tenant identifiers. Tenant-owned repositories added
query using both the resource ID and the principal's Organization ID. Project
and integration queries apply that rule; a cross-tenant Project ID is reported
as not found. Composite foreign keys and uniqueness constraints provide the
database boundary. This decision is recorded in
[ADR-0001](adr/0001-shared-schema-tenant-isolation.md).

## GitHub connection credentials

Each Project has at most one create-only GitHub integration. The submitted
owner and repository are trimmed, lowercased, and validated before persistence.
The same canonical repository may be connected by different Organizations but
only once inside one Organization.

ReleaseFlow creates a random UUID webhook identity and a 32-byte random signing
secret. The secret is returned only from the successful creation response and
is never exposed by Project reads. The UI renders the creation result directly
with `Cache-Control: no-store` rather than putting plaintext in a redirect or
session flash value.

At rest, AES-256-GCM stores a random 12-byte nonce and authenticated ciphertext.
Additional authenticated data binds the Organization, Project, integration,
and canonical repository to prevent ciphertext relocation. Startup requires a
Base64-encoded 32-byte key from `RELEASEFLOW_CREDENTIAL_MASTER_KEY`. Key and
credential material are never logged. See
[ADR-0002](adr/0002-per-integration-webhook-credentials.md).

## HTTP security and errors

Spring Security uses server-side sessions. CSRF remains enabled for REST and UI
writes, including registration, login, and logout. Thymeleaf inserts hidden
tokens into forms; REST clients obtain a token from `GET /api/csrf`. Anonymous
API requests receive a 401 problem response instead of an HTML redirect.

REST failures use `application/problem+json` and a stable `code`, while
UI validation displays the same application errors next to the relevant field.
Session cookies are HttpOnly and SameSite=Lax; deployments using HTTPS must set
the secure-cookie environment switch.

## Development direction

New code is grouped by product capability. A capability starts with direct,
readable classes and gains internal layers only when implemented behavior needs
them. GitHub configuration performs no provider call, access-token validation,
historical import, or webhook processing. There is no background worker or
separately deployed frontend in the current system.
