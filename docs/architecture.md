# Architecture

## Implemented shape

ReleaseFlow is one Spring Boot application built from one Maven module and
packaged as one executable JAR. The implemented capabilities are `status`,
`account`, `project`, `change`, `category`, `audience`, `release`,
`translation`, the provider-neutral `source` vocabulary, the `github` and
`gitlab` API clients that `project` and `change` share, and shared
`configuration`:

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
GET  /projects                -> Project and source configuration UI
POST /projects                -> ProjectService
POST /projects/{id}/sources   -> IntegrationSourceService (administrator)
GET  /api/projects            -> tenant-scoped Project list
POST /api/projects            -> ProjectService
GET  /api/projects/{id}/sources -> IntegrationSourceService (every member)
POST /api/projects/{id}/sources -> IntegrationSourceService (administrator)
POST /projects/{id}/sources/{sourceId}/token -> IntegrationSourceService (administrator)
PUT  /api/projects/{id}/sources/{sourceId}/token -> IntegrationSourceService (administrator)
GET  /api/projects/{id}/imports -> SourceImportService (every member)
POST /[api/]projects/{id}/sources/{sourceId}/imports[/resume] -> SourceImportService (administrator)
POST /webhooks/github/{webhookId} -> GitHubWebhookService (signed, sessionless)
POST /webhooks/gitlab/{webhookId} -> GitLabWebhookService (proven, sessionless)
GET  /changes                 -> ChangeInboxService (Change Inbox UI)
GET  /api/projects/{id}/changes -> ChangeInboxService
POST /projects/{id}/changes/{changeId}/ai-classification -> ChangeAiClassificationService
POST /api/projects/{id}/changes/{changeId}/ai-classification -> ChangeAiClassificationService
POST /projects/{id}/changes/{changeId}/review -> ChangeReviewService
POST /api/projects/{id}/changes/{changeId}/review -> ChangeReviewService
GET  /releases                -> ReleaseService (Releases UI, ?project=&status=)
POST /projects/{id}/releases[/{releaseId}[/schedule|/discard|/changes|/changes/{changeId}/remove]] -> ReleaseService
POST /projects/{id}/releases/{releaseId}/{request-review|approve|return-to-draft|publish} -> ReleaseService
POST /projects/{id}/releases/{releaseId}/changes/{changeId}/decision -> ReleaseService
GET  /projects/{id}/releases/{releaseId} -> ReleaseService (release page)
GET|POST /api/projects/{id}/releases -> ReleaseService
GET|PUT|DELETE /api/projects/{id}/releases/{releaseId} -> ReleaseService
PUT  /api/projects/{id}/releases/{releaseId}/schedule -> ReleaseService
GET  /api/projects/{id}/releases/{releaseId}/available-changes -> ReleaseService
POST /api/projects/{id}/releases/{releaseId}/changes -> ReleaseService
DELETE /api/projects/{id}/releases/{releaseId}/changes/{changeId} -> ReleaseService
POST /api/projects/{id}/releases/{releaseId}/{request-review|approve|return-to-draft|publish} -> ReleaseService
POST /[api/]projects/{id}/releases/{releaseId}/translations/retry -> ReleaseService, TranslationService
PUT  /api/projects/{id}/releases/{releaseId}/changes/{changeId}/decision -> ReleaseService
POST /projects/{id}/releases/{releaseId}/changes/{changeId}/summary -> ReleaseService, ChangeSummaryService
POST /projects/{id}/releases/{releaseId}/notes/{noteId} -> ReleaseService
PUT  /api/projects/{id}/releases/{releaseId}/changes/{changeId}/summary -> ReleaseService, ChangeSummaryService
GET  /api/projects/{id}/releases/{releaseId}/{notes|note-previews} -> ReleaseService
PUT  /api/projects/{id}/releases/{releaseId}/notes/{noteId} -> ReleaseService
GET  /api/projects/{id}/releases/{releaseId}/notes/{noteId}/download -> ReleaseService
GET  /api/projects/{id}/release-assignments -> ReleaseService
GET  /categories              -> CategoryService, CategorySuggestionService (Categories UI, administrator)
POST /categories[/{categoryId}[/archive|/unarchive]] -> CategoryService (administrator)
POST /categories/suggestions/{suggestionId}/decision -> CategorySuggestionService (administrator)
GET  /api/categories          -> CategoryService (every member)
POST /api/categories, PUT|DELETE /api/categories/{categoryId}, POST /api/categories/{categoryId}/unarchive -> CategoryService (administrator)
GET  /api/category-suggestions, POST /api/category-suggestions/{suggestionId}/decision -> CategorySuggestionService (administrator)
GET  /audiences[/new|/{audienceId}] -> AudienceService (Audiences UI, administrator)
POST /audiences[/{audienceId}[/delete|/reset-to-preset]], /audiences/preview -> AudienceService (administrator)
GET|POST /api/audiences, GET|PUT|DELETE /api/audiences/{audienceId} -> AudienceService (administrator)
POST /api/audiences/{audienceId}/reset-to-preset, /api/audiences/preview -> AudienceService (administrator)
POST /audiences/{audienceId}/templates/{language}, PUT /api/audiences/{audienceId}/templates/{language} -> AudienceService (administrator)
POST /audiences/release-languages -> ReleaseLanguageService (administrator)
GET  /api/organization/release-languages -> ReleaseLanguageService (every member)
PUT  /api/organization/release-languages -> ReleaseLanguageService (administrator)
```

REST and Thymeleaf registration call the same transactional application
service. Project REST and UI controllers likewise call the same Project and
integration source services. One short registration transaction creates an
Organization and its ADMIN AppUser, and publishes `OrganizationRegistered`,
which `CategoryService` and `AudienceService` handle in the same transaction to
seed the default categories and the preset audiences.
Passwords are encoded with BCrypt before persistence; neither the hash nor the
submitted password is returned.

## Roles and invitations

Registration creates an `ADMIN`. Accepting an invitation creates a `MEMBER` in
the invitation's Organization. Only administrators may use `/members`,
`/api/members`, `/api/invitations/**`, and the endpoints that connect a
source, set its token, or import its history. `SecurityConfiguration` enforces this, and `InvitationService`
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

integration_sources (github_integrations before V18)
  id (UUID PK)
  organization_id
  project_id (composite FK with organization_id -> projects; several per Project)
  source_type (GITHUB | GITLAB) + external_project_key (owner/repository or
    group/project, unique per Organization and source type)
  repository_owner + repository_name (GitHub only)
  api_base_url (GitLab only, an allowlisted instance)
  webhook_auth_mode (GITHUB_HMAC | GITLAB_SIGNING_TOKEN | GITLAB_SECRET_TOKEN)
  webhook_id (globally unique)
  secret_nonce + secret_ciphertext
  connection_status, last_sync_at, last_error_code
  created_at
  last_delivery_at (nullable, last accepted webhook delivery)

changes
  id (UUID PK)
  organization_id
  project_id (composite FK with organization_id -> projects)
  pull_request_number (unique per Project)
  title, description, author_login, labels (text[])
  target_branch, merge_commit_sha, merged_at, url
  delivery_id (the delivery that recorded the change, when the provider named one)
  received_at
  category (code), category_display_name, category_group (snapshot of the catalog category)
  breaking, needs_review, classification_reasons (text[])
  classification_source (RULES | AI | SUGGESTION | HUMAN), ai_status (NOT_REQUESTED | SUCCEEDED | FAILED)
  ai_model, ai_failure, ai_attempted_at
  reviewed_by (composite FK with organization_id -> app_users), reviewer_name, reviewed_at
  neutral_summary (jsonb), content_language, audience_narratives (jsonb, keyed by audience code)
  summary_edited_by (composite FK with organization_id -> app_users), summary_editor_name, summary_edited_at
  context_score (0-100), context_status (SUFFICIENT | INSUFFICIENT), context_reasons (jsonb); null when not assessed

duplicate_candidates
  id (UUID PK)
  (change_id, organization_id, project_id) and (duplicate_of_id, organization_id, project_id) FK -> changes, ON DELETE CASCADE
  change_id + duplicate_of_id unique, distinct; similarity (0-1), evidence (jsonb: title, content, paths)
  status (OPEN | CONFIRMED | DISMISSED), decided_by (composite FK -> app_users), decider_name, created_at, decided_at

category_definitions
  id (UUID PK)
  organization_id (FK -> organizations.id)
  code ([A-Z][A-Z0-9_]*, unique per Organization, fixed), display_name
  category_group (FEATURE | FIX | PERFORMANCE | DOCUMENTATION | MAINTENANCE | OTHER)
  system_category (only UNKNOWN; always active, group OTHER), active, created_at, updated_at

category_suggestions
  id (UUID PK)
  (change_id, organization_id, project_id) FK -> changes, ON DELETE CASCADE; change_id unique
  proposed_code, proposed_name, proposed_group, rationale
  status (PENDING_REVIEW | APPROVED | MAPPED | REJECTED), resolved_code
  decided_by (composite FK -> app_users), decider_name, created_at, decided_at

audience_definitions
  id (UUID PK)
  organization_id (FK -> organizations.id); (id, organization_id) unique
  code ([a-z][a-z0-9_]*, unique per Organization, fixed), display_name
  communication_intent, template_body (Mustache, at most 10000 characters)
  preset, created_at, updated_at

releases
  id (UUID PK)
  organization_id, project_id (composite FK -> projects)
  version, summary
  status (DRAFT | IN_REVIEW | APPROVED | PUBLISHED)
  version unique per Project, ignoring case
  created_at, updated_at, planned_release_at
  approved_at, approved_by (composite FK -> app_users), approver_name
  published_at, published_by (composite FK -> app_users), publisher_name

release_changes
  release_id + change_id (PK); change_id unique
  (release_id, organization_id, project_id) FK -> releases, ON DELETE CASCADE
  (change_id, organization_id, project_id) FK -> changes
  added_at

release_change_reviews
  release_id + change_id (PK)
  (release_id, change_id, organization_id, project_id) FK -> release_changes, ON DELETE CASCADE
  action (APPROVE | EDIT), note
  reviewer_id (composite FK with organization_id -> app_users), reviewer_name, decided_at

release_audience_notes (written while APPROVED, immutable once PUBLISHED)
  id (UUID PK); release_id + audience_id unique
  (release_id, organization_id, project_id) FK -> releases, ON DELETE CASCADE
  (audience_id, organization_id) FK -> audience_definitions (blocks deleting a used audience)
  audience_code, audience_name, language, template_body_snapshot
  content, auto_rerender, last_edited_by (composite FK -> app_users), last_editor_name
  created_at, updated_at

release_notes (legacy, immutable; releases published before V13)
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

## Source connection credentials

A Project may have several create-only integration sources: GitHub repositories
and GitLab projects. Owners, repository names, and GitLab project paths are
trimmed, lowercased, and validated before persistence; which fields a request
needs depends on its type, and `IntegrationSourceRequestValidator` reports the
missing ones against those fields so REST and Thymeleaf both show them. The same
canonical key may be connected by different Organizations, but only once inside
one Organization and per source type
(`integration_sources_external_unique`). V18 renamed `github_integrations` to
`integration_sources` in place, so every row kept its ID, webhook ID,
repository, and ciphertexts; V19 made the repository columns GitHub's alone and
added `api_base_url` and `webhook_auth_mode`. See
[ADR-0016](adr/0016-integration-sources-and-history-import.md) and
[ADR-0017](adr/0017-gitlab-sources.md).

A GitLab source also names its instance, the only provider address a request may
choose. `GitLabBaseUrl` accepts it only as a plain HTTP or HTTPS address — no
credentials, query, or fragment — whose origin is in
`releaseflow.gitlab.allowed-hosts` (`gitlab.com` by default), answering
`400 gitlab_base_url_invalid` or `400 gitlab_host_not_allowed` otherwise. An
allowlist entry is `host` or `host:port`, which means HTTPS, or a full origin
such as `http://gitlab.internal:8080`. The allowlist is checked again before
every call, and `GitLabApiClient` never follows a redirect, so a redirect cannot
move a call to another host.

ReleaseFlow creates a random UUID webhook identity and a 32-byte random signing
secret for every source, whichever provider it belongs to. The secret is
returned only from the successful creation response and is never exposed by
Project reads. The UI renders the creation result directly with
`Cache-Control: no-store` rather than putting plaintext in a redirect or session
flash value.

At rest, AES-256-GCM stores a random 12-byte nonce and authenticated ciphertext.
Additional authenticated data binds the Organization, Project, and source, plus
the canonical repository for GitHub or the source type and project path for
GitLab, to prevent ciphertext relocation. GitHub's authenticated data is
unchanged since PR #3, so no ciphertext was ever rewritten. Startup requires a
Base64-encoded 32-byte key from `RELEASEFLOW_CREDENTIAL_MASTER_KEY`. Key and
credential material are never logged. See
[ADR-0002](adr/0002-per-integration-webhook-credentials.md).

An administrator may add or replace one access token per source.
`IntegrationSourceService.replaceToken` reads the source in one short
transaction, asks its provider outside any transaction — GitHub with
`GitHubApiClient.checkPullRequestAccess`
(`GET /repos/{owner}/{repo}/pulls?state=closed&per_page=1`), GitLab with
`GitLabApiClient.checkProjectAccess` (`GET /api/v4/projects/{key}` with
`PRIVATE-TOKEN`) — and stores the token in a second short transaction. The token
uses the same cipher and authenticated data as the secret plus a
`github-access-token` or `gitlab-access-token` purpose line, so the two
ciphertexts cannot be swapped. Project reads report only whether a token exists
and when it was set. `SourceAccess` is the single way the `change` capability
obtains a source's coordinates and decrypted token, always by Organization,
Project, and source ID. Saving a token marks the source `ACTIVE` again. See
[ADR-0008](adr/0008-durable-change-processing.md).

## GitHub webhook intake

Both webhook paths are served by one Spring Security filter chain matching
`/webhooks/**`: it permits anonymous requests, disables CSRF, creates no
session, and saves no request. Each provider's own verifier establishes trust
instead. A delivery larger than `releaseflow.webhooks.max-body-bytes` (1 MiB) is
refused with `413 webhook_payload_too_large` before any source is looked up, so
the limit reveals nothing about which sources exist.

For GitHub, trust comes only from the signature, verified in this order:

1. `GitHubWebhookVerifier` in the `project` capability looks the integration up
   by the untrusted webhook ID. This is the one lookup without an Organization
   ID, as anticipated by ADR-0002.
2. It decrypts that integration's secret with its tenant-bound authenticated
   data, computes HMAC-SHA256 over the raw request bytes, and compares it with
   `X-Hub-Signature-256` in constant time. An unknown or malformed webhook ID,
   a missing or malformed header, a wrong signature, and an undecryptable
   secret all produce the same `401 webhook_signature_invalid` response.
3. Only then does `GitHubWebhookService` in the `change` capability parse the
   JSON. The Organization and Project come from the verified source; any
   tenant field in the payload is ignored. A `repository.full_name` that does
   not match the configured repository case-insensitively, or a pull request
   without one, is rejected with `422 webhook_repository_mismatch`.

`ping` is acknowledged. A `pull_request` delivery with action `closed` and
`merged: true` is normalized into a `changes` row; other events and actions are
acknowledged and ignored. Signed but malformed deliveries receive
`400 webhook_payload_malformed`, and nothing is written for any rejected
delivery.

Idempotency relies on the `changes_source_external_unique` constraint on
`(source_id, external_id)`, the pull request number within its source.
`ChangeIntake`, shared by webhook deliveries and history imports, checks for an
existing row first and treats a concurrent unique violation as a duplicate, so
redeliveries return `200 duplicate`. The first
delivery ID is retained on the row. Every accepted delivery advances the
integration's `last_delivery_at`, which the Projects page shows as setup step
three. Intake stays short: one transaction inserts the change, `PROCESSING`,
Unknown, and in review, together with a `PENDING` row in
`change_processing_jobs`. The webhook request makes no GitHub call.

## GitLab webhook intake

`POST /webhooks/gitlab/{webhookId}` works the same way, with the check the
source was set up for. `GitLabWebhookVerifier` resolves the source by the
untrusted webhook ID, requires it to be a GitLab source, and decrypts its secret
with that source's authenticated data. Then, in `GITLAB_SIGNING_TOKEN` mode, it
requires `webhook-id`, `webhook-timestamp`, and `webhook-signature`, a timestamp
within `releaseflow.gitlab.webhook-clock-skew` (five minutes) of the clock, and
a signature of `id.timestamp.body` under the Base64-decoded secret, written as
`v1,` and Base64; the header may hold several space-separated candidates, every
one of which is compared. In `GITLAB_SECRET_TOKEN` mode it compares
`X-Gitlab-Token` with the secret in constant time. `WebhookSignatures` holds the
arithmetic both providers share. Every failure answers the same
`401 webhook_signature_invalid`.

`GitLabWebhookService` then parses the JSON. An event that is not
`object_kind: merge_request` is acknowledged and ignored without reading further.
A `project.path_with_namespace` that is not the connected project is rejected
with `422 webhook_repository_mismatch`, and only `object_attributes.action:
merge` is recorded. `MergedMergeRequest` normalizes the delivery into the same
`MergedPullRequest` a GitHub pull request becomes: `iid` is the number, the
merge commit falls back to the squash commit and then to the last commit, and
the merge time falls back to the last update, which older GitLab instances write
as `2026-09-10 09:14:22 UTC`.

GitLab identifies a delivery only in its signing mode, and not always with a
GUID, so a change records `X-Gitlab-Event-UUID` when it parses as a UUID and
nothing otherwise. `changes_import_has_no_delivery` therefore only requires that
an imported change has no delivery ID, while `GitHubWebhookService` still
demands `X-GitHub-Delivery`.

## Change processing

`ChangeProcessingWorker` runs every second on the scheduler and drains due
jobs. Each step is its own short transaction, and the provider and AI calls
happen between them:

1. Jobs left `ENRICHING` for more than ten minutes return to `PENDING`; jobs
   left `CLASSIFYING` become `FALLBACK_REQUIRED`.
2. One due `PENDING` or `FALLBACK_REQUIRED` job is locked with
   `FOR UPDATE SKIP LOCKED`. A `FALLBACK_REQUIRED` job is completed at once
   from the recorded files, with the rules and a `CLASSIFIER_FALLBACK`
   trigger, never asking the AI again. A `PENDING` job is marked `ENRICHING`,
   its attempt counted, and its claim time recorded, so several workers never
   take the same job.
3. Without a transaction, `SourceAccess` supplies the coordinates and token of
   the change's source, and the `ChangedFileCollector` of its type lists the
   files, 100 per page: GitHub's pull request files for at most five pages, or
   GitLab's merge request diffs for at most ten. A full last page, a refused or
   missing token, or an invalid response is final, and so is a diff GitLab
   reports as collapsed, too large, or truncated, which makes the whole list
   unavailable rather than short. Timeouts, network errors, rate limits, and 5xx
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

## History import

ADR-0016 imports a source's merged pull and merge requests of the last 90 days,
and ADR-0017 gives each provider its own reader.

- **Jobs.** `SourceImportService` queues a `HISTORICAL_IMPORT` row in
  `source_sync_jobs` for a source with a token, with its window, cursor
  `1:0`, and `releaseflow.import.item-limit` (500). The partial unique index
  `source_sync_jobs_one_active` allows one `PENDING`, `RUNNING`, or
  `RETRY_SCHEDULED` job per source; a second request returns
  `409 source_sync_in_progress`. Resuming a `PARTIAL` or `FAILED` job puts it
  back to `PENDING` with its cursor, a zero imported count, and zero attempts.
- **Readers.** A `SourceHistoryReader` per source type turns one page into
  normalized items, and `SourceHistoryReaders` fails at startup if a type has
  none. `GitHubHistoryReader` asks
  `GET /repos/{owner}/{repo}/pulls?state=closed&sort=updated&direction=desc&per_page=100&page=N`,
  newest update first, so an item updated before the window means there is
  nothing older left and it ends the scan. `GitLabHistoryReader` asks
  `GET /api/v4/projects/{key}/merge_requests?state=merged&order_by=updated_at&sort=asc&per_page=100&page=N&updated_after=…`,
  so the list only grows at its end and an older item never appears.
- **Worker.** `SourceImportWorker` runs every second (off in tests, like the
  change worker). It releases `RUNNING` jobs claimed more than ten minutes ago,
  claims one due job with `FOR UPDATE SKIP LOCKED`, and then, outside any
  transaction, reads pages through that reader. From the cursor's offset, each
  item is counted as scanned; one merged inside the window goes through
  `ChangeIntake.record(..., IMPORT)`, which creates the change and its
  processing job or reports a duplicate. At the item limit the job becomes
  `PARTIAL` with the cursor on the next item, and a page the provider says is
  its last completes it. The cursor and counts are saved in a short transaction
  after every page.
- **Failures.** A 429, or a 403 with `Retry-After` or an exhausted rate limit,
  waits `min(Retry-After or the reset time, one hour)`; a 5xx or network
  failure waits `min(300, 2^attempt)` seconds; the fifth attempt fails. 401,
  404, and other 403 responses fail at once and mark the source `ERROR` through
  `IntegrationSourceService.recordSync`, which also records `last_sync_at` and
  the error code, `github_unavailable` or `gitlab_unavailable` naming the
  provider that could not be reached. An unreadable item is skipped with a
  warning.
- **Changes.** Imported changes have `origin = IMPORT` and no `delivery_id`,
  which `changes_import_has_no_delivery` enforces. The Change Inbox
  shows each source's latest import with Import and Resume actions for
  administrators, and an **Imported** badge on the cards.

Because GitHub orders the list by update time, a pull request updated while an
import is running can move between pages and be skipped or seen twice. Seeing
it twice is harmless, and webhooks cover anything merged after connection. A
GitLab import asks for the same window every time, so a merge request updated
during the run moves to the end of the list rather than past the cursor.

## Deterministic classification

`ChangeClassifier` runs in the worker after the changed files are known. It
uses the pull request title, labels, description, and file list, and never
performs network I/O itself.

| Signal | Rule | Effect |
| --- | --- | --- |
| Title type | `^(feat\|fix\|perf\|docs\|refactor\|chore\|ci\|build\|test)(scope)?(!)?: text`, case-insensitive | Feature, Fix, Performance, Documentation, or Maintenance group |
| Title `!` | Marker after the type or scope | Breaking |
| Label | `enhancement`, `feature`; `bug`, `bugfix`; `performance`; `documentation`, `docs`; `dependencies`, `maintenance`, `chore`, `refactor` | Category, only without a title type |
| Breaking label | `breaking-change`, `breaking change`, `breaking` | Breaking |
| Footer | A description line starting with `BREAKING CHANGE:` or `BREAKING-CHANGE:` | Breaking |
| Files | Every changed file is in `docs/` or ends in `.md`, `.adoc`, or `.rst` | Documentation, before the title type |
| Sensitive path | A changed or previous path matches `releaseflow.classification.sensitive-paths` or one of the Project's additions | `SENSITIVE_PATH` review trigger |
| No file list | The files could not be listed | `CHANGED_FILES_UNAVAILABLE` review trigger |

Each category rule names a group and a preferred code (the former fixed value,
such as `FIX`). The Organization's active catalog, passed in by the caller,
decides the category: the preferred one if active, else the first active
category of the group by code. A group without an active category locks nothing
and adds the reason "No active category in the … group". A title type outranks
labels, and every matched rule is kept as a reason, so a disagreement stays
visible. Labels naming different categories without a title
type, or no matching rule at all, produce Unknown. `needs_review` is true for
every breaking, Unknown, or triggered change; the `changes_review_required`
and `changes_triggers_require_review` check constraints enforce that invariant
in the database as well. Review triggers are stored as typed JSON
(`{type, detail}`) beside the textual reasons. `SensitivePathRules` compiles
the configured JDK globs at startup, also matching `**/x` patterns at the
root, and refuses to start with an invalid pattern or an empty list. Flyway `V4`
assigns Unknown, needs review, and the reason "Recorded before rule-based
classification" to changes recorded before it ran.

### Sensitive paths per Project

ADR-0014 lets administrators add patterns for one Project. The baseline stays
in `SensitivePathRules`; `ProjectSensitivePathService` stores a Project's
additions in `project_sensitive_paths` (Flyway `V16`: one row per Project with
the patterns as a JSON array of at most 100, composite foreign keys to the
Project and to the administrator who last changed them). The worker, a job
completed without AI, and an AI retry all ask the service for the Project's
`SensitivePaths`, the baseline followed by the additions, so the baseline can
never be removed, and changes classified earlier keep their triggers.
`SensitivePathAdditions` trims the patterns, drops blank lines and repeats,
and rejects more than 100, any longer than 256 characters, or one that does not
compile, with `400 invalid_sensitive_paths`. Everything lives in the `change`
package, which already depends on `project`, so the page is its own
`/projects/{projectId}/sensitive-paths` rather than part of the Projects page.
Members may read it and `GET /api/projects/{projectId}/sensitive-paths`;
`SecurityConfiguration` limits the `PUT` and the page's `POST` to
administrators. See [ADR-0014](adr/0014-project-sensitive-paths.md).

## Change Inbox

`ChangeInboxService` serves both `GET /changes` and
`GET /api/projects/{projectId}/changes`. It first resolves the Project through
`ProjectService.get` with the principal's Organization ID, so another tenant's
Project is reported as not found. It then queries changes by both Organization
and Project ID, optionally filtered by category code, review status, and
insufficient context, newest merge first. Cards show each change's category snapshot, a pending category
proposal, and, for administrators, the forms that decide it. Unsupported filter values return `400 invalid_change_filter`. The
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
mistyped answer as a whole. The request names the Organization's audiences
(code and communication intent, from `AudienceService.briefs`), and the
response schema is built for each request with one required narrative string
per audience code, which OpenAI strict mode and Anthropic Structured Outputs
enforce. The parser reads only the requested codes; a missing, empty, or
mistyped narrative is left out without failing the answer. Every provider refuses to run inside a transaction
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
change stores `neutral_summary` (JSONB), `audience_narratives` (JSONB),
`content_language`, `ai_provider`, and `ai_model`. A summary that a person
wrote through `ChangeSummaryService` records its writer and is never replaced
by a later AI answer. `changes_neutral_summary_consistent`,
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

## Review signals

Two signals add review triggers after classification; they never settle,
merge, or remove a change. See
[ADR-0013](adr/0013-context-sufficiency-and-duplicates.md).
`ClassificationSettings` reads and validates their thresholds at startup
(`releaseflow.classification.context-threshold`, default 60;
`releaseflow.classification.duplicate-threshold`, default 0.82).

- **Context sufficiency.**
  - The AI request carries `context_threshold`, and the schema requires
    `context_sufficiency` with a score and reasons. The parser rounds and
    clamps the score and keeps at most five reasons.
  - `ContextSufficiency.assess` lowers the score with the fixed caps for an
    empty description (30), a short title (50), and a generic title (40), and
    merges their reason codes after the AI's.
  - `ChangeAiMerge` assesses only a successful answer and adds one
    `CONTEXT_INSUFFICIENT` trigger below the threshold.
  - `changes_context_consistent` keeps the three context columns together, in
    range, and present only with a successful AI attempt.
- **Possible duplicates.**
  - `DuplicateDetector` runs in the worker's completing transaction, also when
    a stalled classification is finished without AI. It reads at most 500
    other processed changes of the Project received in the last 180 days by the
    application clock.
  - `DuplicateSimilarity` compares title and content trigrams after NFKC,
    lower-casing, and collapsing other characters, and the changed and
    previous file paths when both sides list files.
  - Pairs at or above the threshold become `duplicate_candidates` rows (at
    most five, most similar first) and `DUPLICATE_CANDIDATE` triggers on the
    new change through `Change.addReviewTriggers`, which only adds.
  - `DuplicateCandidateService` lists pairs with both sides for the Inbox and
    REST, and records one decision per pair; a second decision is
    `409 duplicate_candidate_decided`.

## Category catalog

`category` owns the catalog and the proposals; it depends on neither `change`
nor `release`. See [ADR-0012](adr/0012-category-catalog.md).

- `CategoryService` serves the pages and REST (writes are administrator only by
  URL rule), the rules and the AI (`active`), and reviews (`find`, active codes
  only, ignoring case).
- `CategoryRef` is the snapshot a change stores: code, display name, and group.
  `CategoryRef.normalize` upper-cases a code, turns `-` and spaces into `_`, and
  rejects anything but `[A-Z][A-Z0-9_]*` of at most 64 characters.
- A system category (only UNKNOWN) keeps its group and cannot be archived
  (`409 category_system`). Archiving is reversible and never touches changes.
- The AI request lists the active categories, and the schema's `category` enum
  is their codes. `suggested_category` is always an object whose empty code
  means no proposal, which keeps OpenAI strict mode and Anthropic Structured
  Outputs simple.
- `AiClassificationParser` rejects a code outside the catalog as invalid. It
  keeps a proposal only with an Unknown answer, a valid code not already
  listed, and no rules lock. `ChangeAiMerge` then adds a
  `CATEGORY_SUGGESTION_PENDING` trigger, and the worker, or the AI retry,
  stores the proposal through `CategorySuggestionService.propose` in the
  transaction that records the result.
- `CategorySuggestionService.decide` checks that the proposal is still pending
  before doing anything.
  - APPROVED activates the proposed category, creating it or restoring an
    archived one with that code; MAPPED needs an active category; REJECTED
    adds nothing.
  - It then publishes `CategorySuggestionDecided`.
  - `CategorySuggestionDecisionListener` in `change` gives the category to the
    change if it is still Unknown and unreviewed, with the source
    `SUGGESTION`. `needs_review` and the trigger stay until a person reviews
    the change.

Flyway `V14` enforces the catalog with these constraints:

- `category_definitions_code_valid`, `category_definitions_group_known`,
  `category_definitions_system_active`, and
  `category_definitions_unknown_is_system` keep the catalog valid;
- `changes_category_valid` keeps the snapshot valid, UNKNOWN always in the
  OTHER group;
- `changes_ai_state_consistent` requires a successful AI attempt for the
  `SUGGESTION` source;
- `category_suggestions_decision_recorded` keeps each suggestion's decision
  consistent.

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

## Release lifecycle

The `release` capability reads changes only through the public
`ChangeInboxService.releasableChanges` and `ChangeInboxService.changes`
methods, both scoped by Organization and Project, and records reviews through
the public `ChangeReviewService.review`, and records summaries through the
public `ChangeSummaryService.edit`. `change` does not depend on `release`;
both depend on `audience`. `ReleaseService` looks a Project up through
`ProjectService.get` and a release by ID, Organization ID, and Project ID.

A release moves `DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED`; see
[ADR-0010](adr/0010-release-review-lifecycle.md). `Release` owns the status
checks and the service checks the release's changes and decisions.

| Operation | Allowed in | Also requires |
| --- | --- | --- |
| Edit version or summary, add changes | `DRAFT` | a processed change that is in no other release |
| Remove a change (reject during review) | `DRAFT`, `IN_REVIEW` | |
| Request review | `DRAFT` | at least one change |
| Record a decision | `IN_REVIEW` | a change of this release |
| Approve (writes the audience notes) | `IN_REVIEW` | a decision on every change |
| Edit a change's summary | `IN_REVIEW`, `APPROVED` | a change of this release |
| Edit an audience note | `APPROVED` | a note of this release |
| Return to draft | `IN_REVIEW`, `APPROVED` | |
| Schedule, discard | any status before `PUBLISHED` | a future time, or none |
| Publish | `APPROVED` | its audience notes |

A Project may prepare several releases at once. Any change that finished
processing can be added, including one that still needs review.
`release_changes_change_unique` keeps each change in one release. The
composite foreign keys on `(id, organization_id, project_id)` make the
database reject a change joining a release of another Project or tenant.

A decision is `APPROVE` or `EDIT` and carries the category and breaking flag
the reviewer saw. `APPROVE` must match the change's current classification
(`409 classification_changed` otherwise) and cannot confirm an Unknown
change. Both actions call `ChangeReviewService.review`, so the change records
the reviewer exactly as an Inbox review does, and a `release_change_reviews`
row records the release's decision, the reviewer, an optional note, and the
time. A later decision on the same change replaces the row. Rejecting a
change removes it from the release, and the cascade removes its decision.
Approval records the approver and time and requires a decision on every
change, so no approved release contains a change that still needs review.
Returning to draft deletes the decisions, the approval, and the notes; the
reviews and summaries on the changes stay. Discarding deletes the release, and the cascade makes its changes
available again.

Flyway `V12` enforces the lifecycle in PostgreSQL too. Rows are added to
`release_changes` only while the release is a draft, are never updated, and
are deleted only before approval. Decisions are written only while the release
is in review and deleted only before approval. The triggers skip a release
that no longer exists, so discarding an approved release still cascades.
`releases_approval_recorded` keeps the approval columns together, requires
them on `APPROVED`, and forbids them on `DRAFT` and `IN_REVIEW`. Releases
published before `V12` have no approval.

The planned release time is optional and must lie in the future. REST clients
send an ISO-8601 instant with an offset; the page's `datetime-local` input
sends a local time that is read as UTC, and pages show times in UTC.

`ReleaseNotePreview` groups an unpublished release's changes for the REST
`preview` field by category group: breaking changes first and only there, then
Features, Fixes, Performance, Documentation, Maintenance, and Other changes,
sorted by merge time. Titles drop a
leading Conventional Commit prefix. Pages instead preview each audience's note
(see Audiences and release notes).

## Audiences and release notes

`audience` owns the Organization's audiences and their templates; see
[ADR-0011](adr/0011-audience-release-notes.md). `AudienceService` serves the
REST and page controllers (administrator only by URL rule), the AI request
(`briefs`), and the release service (`list`). Creating and deleting audiences
lock the Organization's audience rows, so the limits of one and twenty hold
under concurrent requests. A foreign key from `release_audience_notes` makes
deleting a used audience fail, which is reported as `409 audience_in_use`.
`AudiencePresets` holds the three shipped audiences, with names and template
labels from `messages/audience-presets{,_vi}.properties`. V13 seeds the same
values for Organizations that existed before, and a migration test compares
them.

`AudienceTemplate` wraps JMustache in standards mode with strict sections,
empty strings as false, and no escaping, because the output is Markdown. It
validates a template on save by compiling it and rendering sample values, and
renders one `AudienceItem` (`whatChanged`, `whyChanged`, `technicalDetail`,
`migrationStep`, `narrative`, `pullRequestNumber`, `pullRequestUrl`).
`MarkdownHtml` renders Markdown for pages with CommonMark: raw HTML is
escaped, link targets are sanitized and marked
`rel="nofollow noopener noreferrer"`, and images become links.

`ReleaseNoteDigest` is a pure function that writes one audience's note:

- the title and the escaped release summary;
- "What's New", with a count per section joined by the bundle's list pattern,
  and a breaking warning;
- sections by category group in the order breaking (the flag), features,
  fixes, performance, documentation, maintenance, other, each item rendered by
  the template and its headings demoted two levels outside fenced code.

Labels come from `messages/release-note{,_vi}.properties`, chosen by the note's
language with an English fallback, and counts use `java.text` choice formats.
Without a summary, what changed is the escaped pull request title.

`ReleaseService` uses it as follows:

- **Approval** renders one `AudienceReleaseNote` per audience and release note
  language from the current changes, snapshots the audience's code, name,
  template for that language, and the language, and inserts the notes after the
  release row is flushed as approved (see
  [Release note languages and translation](#release-note-languages-and-translation)). A render failure aborts approval with
  `409 release_note_render_failed`.
- **Editing a note** is allowed only while approved. It stores the text and the
  editor and turns off `auto_rerender`.
- **Editing a summary** is allowed during review or after approval, for a
  change of the release. It calls `ChangeSummaryService.edit`, which keeps
  narratives only for current audience codes, and then renders every automatic
  note again from its stored template.
- **Returning to draft** deletes the notes.
- **Publishing** requires notes (`409 release_notes_missing`).
- **Previews** render the current changes with the current templates and store
  nothing.

The `release_audience_notes_follow_release_status` trigger allows inserts and
updates only while the release is approved. An update may change only the
content and the edit record, and deletes are rejected once the release is
published. `release_audience_notes_edit_recorded` ties `auto_rerender` to the
editor columns.

## Release note languages and translation

ADR-0015 writes a note per audience and **release note language**; see
[ADR-0015](adr/0015-multilingual-release-notes.md).

- **Languages.** `ReleaseLanguageService` (package `audience`) stores the
  Organization's 1–5 target languages in `organization_translation_settings`,
  canonicalized by `OutputLanguage.parse`. Without a row, the output language
  is the only one.
- **Templates.** `audience_template_variants` holds an audience's template for
  one language, unique per audience and language and deleted with the
  audience. `AudienceView.templateFor(language)` returns the variant or the
  main template. Saving the languages, and creating an audience, add the
  missing variants for every target language whose primary language differs
  from the output language: the shipped template in that language for a preset,
  a copy of the main template otherwise.
- **Jobs.** The `translation` package depends on no other domain package.
  `TranslationService.ensure` touches only the database, so approval can call it
  in its transaction. It returns the texts at once when the source and target
  share a primary language or all are blank, returns the existing job's state
  for the same change, target, and `input_hash` (SHA-256 of the source language
  and the texts sorted by key), and otherwise creates a `translation_jobs` row:
  `SUCCEEDED` when every text is in `translation_cache`, `FAILED`
  (`translation_disabled`) without a provider, and `PENDING` otherwise.
- **Worker.** `TranslationWorker` runs every second. It claims one due job with
  `FOR UPDATE SKIP LOCKED`, looks its texts up in the cache, and sends only the
  missing ones to `DeepLTranslator` outside any transaction: `POST
  /v2/translate` with `DeepL-Auth-Key`, batches of at most 50 texts and 120 KiB,
  source as the primary language and English or Portuguese targets with a
  region. A 429, a 5xx, or a network failure is retried up to five attempts with
  `min(300, 2^attempt)` seconds of backoff; 403, 456, other 4xx, and a response
  with the wrong number of translations fail at once. Only fixed codes are
  stored. The result transaction writes the output and the cache and publishes
  `TranslationFinished`; a `RUNNING` job left for ten minutes becomes
  `PENDING` again.
- **Notes.** `ReleaseNoteWriter` localizes the release's changes for one
  language: a summarized change in another language is replaced by
  `ChangeView.withContent` with its translated summary and narratives once
  ready, and keeps its own text otherwise. The note takes the worst state of its
  changes (`FAILED` over `PENDING` over `READY`) in
  `release_audience_notes.translation_status`. Approval writes a note per
  target language and audience from `templateFor`; `ReleaseService` listens to
  `TranslationFinished` in the worker's transaction and renders the automatic
  notes of the change's approved release in that language again. Editing a
  summary renders every automatic note again, which queues the new text.
  `retryTranslations` restarts the failed jobs of an approved release's current
  texts. Editing a note by hand makes it `READY`.
- **Publication.** `publish` refuses a release with a note that is not ready
  (`409 translations_not_ready`), and the `releases_publish_ready_notes`
  trigger refuses it in the database too. Notes are unique per release,
  audience, and language, and downloads are named
  `<version>-<audience>-<language>.md`.
- **Page.** The release page shows a badge per note, a banner while notes are
  translating or failed, and a **Retry translations** button. The
  `translationPoll` Alpine component reads the notes URL from a data attribute,
  polls it every three seconds for up to ten minutes, and reloads once no note
  is pending.

## Release Note publication

`ReleaseService.publish` runs in one transaction. It requires an approved
release with notes that are all ready and marks it `PUBLISHED` with the publisher's ID, name, and
time; from then on the triggers freeze its audience notes. Releases published
before V13 have a single legacy `release_notes` snapshot of sections and
Markdown instead, which reads fall back to. Reads of a published release use
the stored notes, so reclassifying an included change afterwards never changes
what was published. See [ADR-0005](adr/0005-immutable-release-note-snapshots.md)
and [ADR-0011](adr/0011-audience-release-notes.md).

Immutability is enforced twice. The service rejects every operation on a
published release with `409 release_published`. PostgreSQL triggers reject
UPDATE or DELETE on `release_notes`, UPDATE or DELETE on a `PUBLISHED`
release, and writes to its `release_changes`, `release_change_reviews`, and
`release_audience_notes`.
`releases_publication_recorded` keeps status, time, and publisher consistent.
`releases_project_version_unique` prevents a later release from reusing a
published version.

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
