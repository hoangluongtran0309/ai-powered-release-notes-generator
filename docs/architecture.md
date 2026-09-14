# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, `project`, `change`, `release`, and shared `configuration`:

```text
GET  /                         -> Thymeleaf home, or overview when signed in
GET  /register                -> administrator registration form
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
POST /projects/{id}/changes/{changeId}/ai-classification -> ChangeAiClassificationService
POST /api/projects/{id}/changes/{changeId}/ai-classification -> ChangeAiClassificationService
POST /projects/{id}/changes/{changeId}/review -> ChangeReviewService
POST /api/projects/{id}/changes/{changeId}/review -> ChangeReviewService
GET  /releases                -> ReleaseService (Releases UI)
POST /projects/{id}/releases[/{releaseId}[/discard|/changes|/changes/{changeId}/remove]] -> ReleaseService
GET  /projects/{id}/releases/{releaseId} -> ReleaseService (draft page)
GET|POST /api/projects/{id}/releases -> ReleaseService
GET|PUT|DELETE /api/projects/{id}/releases/{releaseId} -> ReleaseService
GET  /api/projects/{id}/releases/{releaseId}/available-changes -> ReleaseService
POST /api/projects/{id}/releases/{releaseId}/changes -> ReleaseService
DELETE /api/projects/{id}/releases/{releaseId}/changes/{changeId} -> ReleaseService
POST /projects/{id}/releases/{releaseId}/publish -> ReleaseService
POST /api/projects/{id}/releases/{releaseId}/publish -> ReleaseService
```

REST and Thymeleaf registration call the same transactional application
service. Project REST and UI controllers likewise call the same Project and
GitHub integration services. One short registration transaction creates an
Organization and its ADMIN AppUser.
Passwords are encoded with BCrypt before persistence; neither the hash nor the
submitted password is returned.

## Roles and invitations

Registration creates an `ADMIN`. Accepting an invitation creates a `MEMBER` in
the invitation's Organization. Only administrators may use `/members`,
`/api/members`, `/api/invitations/**`, and the GitHub integration `POST`
endpoints. `SecurityConfiguration` enforces this, and `InvitationService`
checks the principal's role again. Members can use every other workspace
feature.

`InvitationService` issues a 32-byte base64url token, stores only its SHA-256
hash, and returns the raw token once with `Cache-Control: no-store`. An
invitation expires after `releaseflow.invitations.ttl` (seven days by default),
works once, and can be reissued or revoked. A pending invitation past its
expiry is reported as expired and recorded as such when touched again.

The acceptance link keeps the token in the URL fragment. `/accept-invite` is a
server-rendered form that a small script fills from the fragment; the review
step shows the Organization and email, and the accept step locks the
invitation row, creates the member account, and marks the invitation accepted.
Every unusable token receives the same error. See
[ADR-0006](adr/0006-organization-roles-and-invitations.md).

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
  role (ADMIN | MEMBER)

organization_invitations
  id (UUID PK)
  organization_id
  email (canonical), token_hash (SHA-256 hex, unique)
  status (PENDING | ACCEPTED | REVOKED | EXPIRED; one PENDING per email)
  expires_at, created_by / accepted_by (composite FKs -> app_users)
  created_at, updated_at, accepted_at, revoked_at
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
  classification_source (RULES | AI | HUMAN), ai_status (NOT_REQUESTED | SUCCEEDED | FAILED)
  ai_model, ai_failure, ai_attempted_at
  reviewed_by (composite FK with organization_id -> app_users), reviewer_name, reviewed_at

releases
  id (UUID PK)
  organization_id, project_id (composite FK -> projects)
  version, summary
  status (DRAFT | PUBLISHED; at most one DRAFT per Project)
  version unique per Project, ignoring case
  created_at, updated_at
  published_at, published_by (composite FK -> app_users), publisher_name

release_changes
  release_id + change_id (PK); change_id unique
  (release_id, organization_id, project_id) FK -> releases, ON DELETE CASCADE
  (change_id, organization_id, project_id) FK -> changes
  added_at

release_notes (immutable)
  release_id (PK; composite FK with organization_id, project_id -> releases)
  version, summary, sections (jsonb), markdown, published_at
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

## AI classification

OpenAI is the only AI provider, and it is optional: `OpenAiChangeClassifier`
is enabled only when both `RELEASEFLOW_OPENAI_API_KEY` and
`RELEASEFLOW_OPENAI_MODEL` are set, and startup fails if only one is. A person
requests a suggestion for one change at a time. Only a change with
`category = UNKNOWN` and `classification_source = RULES` is eligible. See
[ADR-0004](adr/0004-openai-classification-as-reviewed-suggestion.md).

`ChangeAiClassificationService` is not transactional. It uses a
`TransactionTemplate` for two short transactions, with the network call
between them:

1. Load the change by ID, Organization ID, and Project ID, and check that it
   is eligible.
2. With no transaction open, call `POST {base-url}/chat/completions` through
   `RestClient`. The request has `store: false`, a strict JSON Schema for the
   category, breaking flag, and rationale, and only the title, labels, target
   branch, and a truncated description. The client refuses to run inside an
   active transaction.
3. Reload the change and record the outcome only if it is still eligible, so
   a concurrent request's result is kept.

A suggestion sets the category, adds the rationale as a reason, can only set
the breaking flag (never clear it), and keeps `needs_review = true`. A
timeout, connection error, non-2xx status, refusal, incomplete answer, or
invalid JSON becomes `ai_status = FAILED` with a fixed message that never
contains the API key or a response body; the change stays Unknown. The
`changes_ai_state_consistent` constraint keeps source, status, model, failure,
and attempt time consistent, and requires review for every AI suggestion. The
REST endpoint reports a stored failure as `502 ai_classification_failed`; the
UI redirects back to the inbox card, which shows the failure and a retry
button.

## Human review

`ChangeReviewService` settles one change in a single transaction. It loads the
change by ID, Organization ID, and Project ID, and requires a concrete
category. It then stores the category and breaking flag exactly as the
reviewer submitted them, sets `needs_review = false`, and records
`reviewed_by`, `reviewer_name`, and `reviewed_at`. Because the reviewer
submits explicit values rather than approving whatever is current, a stale
page cannot confirm a classification it did not show. Any change may be
reviewed, including one already settled by rules or by an earlier review; the
latest review is kept.

Confirming unchanged values keeps the classification source (`RULES` or `AI`).
Changing either value sets it to `HUMAN`. A reviewer may clear a breaking
flag: the invariant that breaking changes require human review is met by the
recorded review itself.

Flyway `V6` adds a unique `(id, organization_id)` key on `app_users` so that
`changes_reviewer_fk` keeps reviewers inside the change's Organization. It
redefines the review constraints so that breaking, Unknown, and AI-suggested
changes can leave review only with a recorded review, a `HUMAN` source always
has one, and a reviewed change is never Unknown. A reviewed change is no longer
Unknown, so it is never eligible for AI classification. The inbox `status`
filter distinguishes `needs-review`, `classified` (settled without a person),
and `reviewed`.

## Draft Releases

The `release` capability reads changes only through the public
`ChangeInboxService.settledChanges` and `ChangeInboxService.changes` methods,
both scoped by Organization and Project. `change` does not depend on
`release`. `ReleaseService` looks a Project up through `ProjectService.get` and
a release by ID, Organization ID, and Project ID.

A Project has at most one draft (`releases_one_draft_per_project`, a partial
unique index). Only settled changes, those with `needs_review = false`, can be
added. No path returns a settled change to review, so a draft never contains
work that still needs review. `release_changes_change_unique` keeps each
change in one release. The composite foreign keys on
`(id, organization_id, project_id)` make the database reject a change joining
a release of another Project or tenant. Discarding a draft deletes it, and the
cascade makes its changes available again.

`ReleaseNotePreview` builds the preview on every read. Breaking changes are
listed first and only there; the rest follow in the order Features, Fixes,
Performance, Documentation, Maintenance, sorted by merge time. Titles drop a
leading Conventional Commit prefix.

## Release Note publication

`ReleaseService.publish` runs in one transaction. It requires a draft with at
least one change and a version unused in the Project. It then builds the
sections from the current changes, renders Markdown with `ReleaseNoteMarkdown`
(escaping Markdown syntax in titles and the summary), inserts the
`release_notes` snapshot, and marks the release `PUBLISHED` with the
publisher's ID, name, and time. Reads of a published release use the stored
sections and Markdown, so reclassifying an included change afterwards never
changes what was published. See
[ADR-0005](adr/0005-immutable-release-note-snapshots.md).

Immutability is enforced twice. The service rejects edit, discard, add,
remove, and a second publish with `409 release_published`. PostgreSQL
triggers reject UPDATE or DELETE on `release_notes`, UPDATE or DELETE on a
`PUBLISHED` release, and any write to `release_changes` of a published
release. `releases_publication_recorded` keeps status, time, and publisher
consistent. `releases_project_version_unique` prevents a later draft from
reusing a published version.

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

## Build, container, and CI

The Maven build is the only build: it runs the tests, compiles the stylesheet,
and packages `target/releaseflow.jar`. The `Dockerfile` runs that build with the
`release` profile in a JDK stage and copies the JAR into a Temurin 21 JRE UBI
minimal image that runs as UID/GID `65534`. The image health check calls
`GET /api/status`. `docker-compose.demo.yml` adds PostgreSQL 17 on a private
network, publishes the application on host loopback only, and requires the
database password and credential master key to be supplied.

GitHub Actions workflows in `.github/workflows` gate pushes and pull requests
to `develop` and `main`: tests and audits (`ci.yml`), CodeQL, dependency
review, a full-history Gitleaks scan, and a container workflow that builds the
image, scans it with Trivy, and smoke tests the Compose stack. Actions are
pinned to commit SHAs, images to digests, and downloaded tools to SHA-256
checksums. See [ADR-0007](adr/0007-ci-and-container-supply-chain.md).

## Development direction

New code is grouped by product capability. A capability starts with direct,
readable classes and gains internal layers only when implemented behavior needs
them. GitHub configuration and webhook intake perform no provider call,
access-token validation, or historical import. The only outbound call is a
person-initiated OpenAI request. There is no background worker or
separately deployed frontend in the current system; the compiled stylesheet
ships inside the application JAR.
