# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, `project`, `change`, `release`, the `github` API client shared by
`project` and `change`, and shared `configuration`:

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
POST /projects/{id}/github-integration/token -> GitHubIntegrationService (administrator)
PUT  /api/projects/{id}/github-integration/token -> GitHubIntegrationService (administrator)
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

An administrator may add or replace one GitHub access token per integration.
`GitHubIntegrationService.replaceToken` reads the integration in one short
transaction, asks GitHub with `GitHubApiClient.checkPullRequestAccess`
(`GET /repos/{owner}/{repo}/pulls?state=closed&per_page=1`) outside any
transaction, and stores the token in a second short transaction. The token uses
the same cipher and authenticated data as the secret plus a
`github-access-token` purpose line, so the two ciphertexts cannot be swapped.
Project reads report only whether a token exists and when it was set.
`GitHubRepositoryAccess` is the single way the `change` capability obtains the
decrypted token, always by Organization and Project ID. See
[ADR-0008](adr/0008-durable-change-processing.md).

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
three. Intake stays short: one transaction inserts the change, `PROCESSING`,
Unknown, and in review, together with a `PENDING` row in
`change_processing_jobs`. The webhook request makes no GitHub call.

## Change processing

`ChangeProcessingWorker` runs every second on the scheduler and drains due
jobs. Each step is its own short transaction, and the GitHub and AI calls
happen between them:

1. Jobs left `ENRICHING` for more than ten minutes return to `PENDING`; jobs
   left `CLASSIFYING` become `FALLBACK_REQUIRED`.
2. One due `PENDING` or `FALLBACK_REQUIRED` job is locked with
   `FOR UPDATE SKIP LOCKED`. A `FALLBACK_REQUIRED` job is completed at once
   from the recorded files, with the rules and a `CLASSIFIER_FALLBACK`
   trigger, never asking the AI again. A `PENDING` job is marked `ENRICHING`,
   its attempt counted, and its claim time recorded, so several workers never
   take the same job.
3. Without a transaction, `GitHubRepositoryAccess` supplies the repository and
   token, and `GitHubApiClient.pullRequestFiles` lists the files, 100 per page
   for at most five pages. A full fifth page, a refused or missing token, or an
   invalid response is final. Timeouts, network errors, rate limits, and 5xx
   responses are retried after 2 and 4 seconds, up to three attempts.
4. The rules classify the change with the result. Without an AI provider, the
   change and job are completed together. With one, the files are recorded on
   the change and the job becomes `CLASSIFYING`; then, with no transaction
   open, the AI is asked once in the Organization's output language; finally
   `ChangeAiMerge` combines the answer (or failure) with the rules and the
   change and job are completed. Every write checks that the job still carries
   this worker's claim, so a claim that went stale and was taken over is
   discarded.

An unexpected failure while collecting files reschedules the job rather than
skipping it, so the change stays visibly Processing. Once the job is
`CLASSIFYING` it is never rescheduled; if it stalls, the stale recovery
completes it without AI. Changes recorded before Flyway `V10` were
marked `COMPLETED` and are never processed.

The database protects the outcome. `changes_processing_unsettled` keeps a
`PROCESSING` change Unknown, in review, unreviewed, without triggers, an AI
result, or a summary (it may carry its recorded files), so it cannot be
released, reviewed, or sent to AI. The service layer
also rejects such a review with `409 change_processing`.
`changes_triggers_require_review` allows review triggers only on changes that
need or have received review.

## Deterministic classification

`ChangeClassifier` runs in the worker after the changed files are known. It
uses the pull request title, labels, description, and file list, and never
performs network I/O itself.

| Signal | Rule | Effect |
| --- | --- | --- |
| Title type | `^(feat\|fix\|perf\|docs\|refactor\|chore\|ci\|build\|test)(scope)?(!)?: text`, case-insensitive | Feature, Fix, Performance, Documentation, or Maintenance |
| Title `!` | Marker after the type or scope | Breaking |
| Label | `enhancement`, `feature`; `bug`, `bugfix`; `performance`; `documentation`, `docs`; `dependencies`, `maintenance`, `chore`, `refactor` | Category, only without a title type |
| Breaking label | `breaking-change`, `breaking change`, `breaking` | Breaking |
| Footer | A description line starting with `BREAKING CHANGE:` or `BREAKING-CHANGE:` | Breaking |
| Files | Every changed file is in `docs/` or ends in `.md`, `.adoc`, or `.rst` | Documentation, before the title type |
| Sensitive path | A changed or previous path matches `releaseflow.classification.sensitive-paths` | `SENSITIVE_PATH` review trigger |
| No file list | The files could not be listed | `CHANGED_FILES_UNAVAILABLE` review trigger |

A title type outranks labels, and every matched rule is kept as a reason, so a
disagreement stays visible. Labels naming different categories without a title
type, or no matching rule at all, produce Unknown. `needs_review` is true for
every breaking, Unknown, or triggered change; the `changes_review_required`
and `changes_triggers_require_review` check constraints enforce that invariant
in the database as well. Review triggers are stored as typed JSON
(`{type, detail}`) beside the textual reasons. `SensitivePathRules` compiles
the configured JDK globs at startup, also matching `**/x` patterns at the
root, and refuses to start with an invalid pattern or an empty list. Flyway `V4`
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

AI is optional. `AiClassifierConfiguration` builds exactly one
`AiChangeClassifier` from `RELEASEFLOW_AI_PROVIDER`, or none:

- `OpenAiCompatibleClassifier` serves `openai` (Chat Completions with a strict
  JSON Schema and `store: false`) and `deepseek` (the same API with
  `json_object` and the schema in the prompt), through `RestClient`;
- `AnthropicChangeClassifier` serves `anthropic` through the official Java SDK,
  with Structured Outputs (`output_config.format`) and SDK retries disabled.

All three share `AiClassificationPrompt` (instructions, response schema, and
the user JSON) and `AiClassificationParser`, which rejects an incomplete or
mistyped answer as a whole. Every provider refuses to run inside a transaction
and turns timeouts, HTTP errors, refusals, truncation, and invalid JSON into a
fixed message that never contains a key or a response body. See
[ADR-0009](adr/0009-automatic-ai-classification.md), which supersedes
[ADR-0004](adr/0004-openai-classification-as-reviewed-suggestion.md).

The change worker calls the AI automatically (see Change processing).
`ChangeAiMerge` combines the rules with the answer:

- the rules' category is kept unless it is Unknown;
- `breaking` is the rules' flag OR the AI's;
- `needs_review` is set when the change is breaking, Unknown, has any review
  trigger, or the AI asked for review;
- otherwise the AI's answer settles the change, and the source is `AI` only
  when the AI chose the category.

A failure keeps the rule result and adds a `CLASSIFIER_FALLBACK` trigger. The
change stores `neutral_summary` (JSONB), `content_language`, `ai_provider`,
and `ai_model`. `changes_neutral_summary_consistent`,
`changes_ai_provider_recorded`, and `changes_ai_state_consistent` keep them
coherent.

`ChangeAiClassificationService` lets a person retry. It is limited to a
completed, unreviewed change whose AI attempt failed, or an Unknown change
recorded before automatic AI. Like the worker, it reads in one short
transaction, calls the provider with none open, and records the result in a
second transaction only if the change is still eligible. Existing triggers are
kept, so a change that needed review still does. The REST endpoint reports a
stored failure as `502 ai_classification_failed`.

## Output language

`organizations.output_language` holds a canonical BCP 47 tag.
`OutputLanguage.parse` accepts any tag whose primary language is an ISO 639
code, turns `_` into `-`, and limits the result to 16 characters.
`OutputLanguageService` serves registration, the Projects page form, and
`GET|PUT /api/organization/output-language`. Changing the tag is
administrator-only by URL rule. The worker reads the tag once per change and
records it as the summary's `content_language`.

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
them. Connecting a repository and receiving webhooks make no provider call,
and there is no historical import. Outbound calls are the configured AI provider,
the GitHub access-token check, and the changed-file listing made by the one
background worker. There is no separately deployed frontend; the compiled
stylesheet ships inside the application JAR.
