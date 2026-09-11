# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, `project`, `change`, and shared `configuration`:

```text
GET  /                         -> Thymeleaf home, or overview when signed in
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
POST /webhooks/github/{webhookId} -> GitHubWebhookService (signed, sessionless)
GET  /changes                 -> ChangeInboxService (Change Inbox UI)
GET  /api/projects/{id}/changes -> ChangeInboxService
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
  last_delivery_at (nullable, last accepted webhook delivery)

changes
  id (UUID PK)
  organization_id
  project_id (composite FK with organization_id -> projects)
  pull_request_number (unique per Project)
  title, description, author_login, labels (text[])
  target_branch, merge_commit_sha, merged_at, url
  delivery_id (X-GitHub-Delivery that recorded the change)
  received_at
  category, breaking, needs_review, classification_reasons (text[])
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

## GitHub webhook intake

`POST /webhooks/github/{webhookId}` is served by its own Spring Security filter
chain: it permits anonymous requests, disables CSRF, creates no session, and
saves no request. Trust comes only from the signature, verified in this order:

1. `GitHubWebhookVerifier` in the `project` capability looks the integration up
   by the untrusted webhook ID. This is the one lookup without an Organization
   ID, as anticipated by ADR-0002.
2. It decrypts that integration's secret with its tenant-bound authenticated
   data, computes HMAC-SHA256 over the raw request bytes, and compares it with
   `X-Hub-Signature-256` in constant time. An unknown or malformed webhook ID,
   a missing or malformed header, a wrong signature, and an undecryptable
   secret all produce the same `401 webhook_signature_invalid` response.
3. Only then does `GitHubWebhookService` in the `change` capability parse the
   JSON. The Organization and Project come from the verified integration; any
   tenant field in the payload is ignored. A `repository.full_name` that does
   not match the configured repository case-insensitively, or a pull request
   without one, is rejected with `422 webhook_repository_mismatch`.

`ping` is acknowledged. A `pull_request` delivery with action `closed` and
`merged: true` is normalized into a `changes` row; other events and actions are
acknowledged and ignored. Signed but malformed deliveries receive
`400 webhook_payload_malformed`, and nothing is written for any rejected
delivery.

Idempotency relies on the `changes_project_pull_request_unique` constraint. The
service checks for an existing row first and treats a concurrent unique
violation as a duplicate, so redeliveries return `200 duplicate`. The first
delivery ID is retained on the row. Every accepted delivery advances the
integration's `last_delivery_at`, which the Projects page shows as setup step
three. Intake is synchronous and short-lived: there is no queue, retry, GitHub
API call, or network I/O inside a database transaction.

## Deterministic classification

`ChangeClassifier` runs inside the webhook request, before the change is
saved. It uses only the pull request title, labels, and description, so it
never performs network I/O.

| Signal | Rule | Effect |
| --- | --- | --- |
| Title type | `^(feat\|fix\|perf\|docs\|refactor\|chore\|ci\|build\|test)(scope)?(!)?: text`, case-insensitive | Feature, Fix, Performance, Documentation, or Maintenance |
| Title `!` | Marker after the type or scope | Breaking |
| Label | `enhancement`, `feature`; `bug`, `bugfix`; `performance`; `documentation`, `docs`; `dependencies`, `maintenance`, `chore`, `refactor` | Category, only without a title type |
| Breaking label | `breaking-change`, `breaking change`, `breaking` | Breaking |
| Footer | A description line starting with `BREAKING CHANGE:` or `BREAKING-CHANGE:` | Breaking |

A title type outranks labels, and every matched rule is kept as a reason, so a
disagreement stays visible. Labels naming different categories without a title
type, or no matching rule at all, produce Unknown. `needs_review` is true for
every breaking or Unknown change; the `changes_review_required` check
constraint enforces that invariant in the database as well. Flyway `V4`
assigns Unknown, needs review, and the reason "Recorded before rule-based
classification" to changes recorded before it ran.

## Change Inbox

`ChangeInboxService` serves both `GET /changes` and
`GET /api/projects/{projectId}/changes`. It first resolves the Project through
`ProjectService.get` with the principal's Organization ID, so another tenant's
Project is reported as not found. It then queries changes by both Organization
and Project ID, optionally filtered by category and review status, newest
merge first. Unsupported filter values return `400 invalid_change_filter`. The
page defaults to the first Project and uses a plain GET form for filters.

## User interface

Pages are server-rendered Thymeleaf templates composed with the Layout Dialect:
`layout/auth` frames the home, sign-in, and registration screens, and
`layout/main` provides the signed-in sidebar shell. `PageModelAdvice` supplies
the signed-in viewer and current path to every page controller.

The stylesheet is compiled from Tailwind CSS 4 and DaisyUI 5 during the Maven
build, which installs a pinned Node.js through `frontend-maven-plugin`.
Alpine.js, served from a WebJar, handles only presentation behavior; forms post
to the same controllers and application services as before. See
[ADR-0003](adr/0003-frontend-toolchain.md).

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
them. GitHub configuration and webhook intake perform no provider call,
access-token validation, or historical import. There is no background worker or
separately deployed frontend in the current system; the compiled stylesheet
ships inside the application JAR.
