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
- Atomic Organization administrator registration through REST and Thymeleaf.
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
- Tenant-scoped Project creation and listing through REST and Thymeleaf.
- Flyway migration `V2` for Projects and one GitHub integration per Project.
- Canonical repository uniqueness within each Organization and cross-tenant
  database constraints.
- Per-integration webhook IDs and 256-bit signing secrets revealed only when
  created.
- AES-256-GCM webhook-secret encryption with random nonces, tenant-bound
  authenticated data, and fail-fast environment key validation.
- Stable REST errors and negative-path coverage for Project ownership and
  GitHub configuration conflicts.
- A workspace UI with a split sign-in screen, sidebar shell, signed-in
  overview, setup progress, copy buttons, and a persistent light/dark theme.
- Tailwind CSS 4, DaisyUI 5, Thymeleaf Layout Dialect, and Alpine.js, with
  Node.js installed by the Maven build.
- A public, sessionless `POST /webhooks/github/{webhookId}` endpoint that
  verifies `X-Hub-Signature-256` with the integration's own decrypted secret
  before reading the payload.
- Tenant identity for webhooks taken only from the verified integration, and
  rejection of deliveries naming another repository.
- Normalized change records for merged pull requests with number, title,
  description, author, labels, target branch, merge commit, merge time, URL,
  and GitHub delivery ID.
- Flyway migration `V3` for changes, one change per pull request within a
  Project, and the last accepted delivery time of each GitHub integration.
- Stable webhook error codes, `ping` handling, and acknowledged but ignored
  unrelated events.
- A "Receive webhooks" setup step and per-repository last-delivery status on
  the Projects page.
- Deterministic change classification from Conventional Commit title types,
  familiar labels, and `BREAKING CHANGE` footers, with the matched rules kept
  as reasons.
- Mandatory review for breaking and Unknown changes, enforced by the
  application and by a database constraint.
- Flyway migration `V4` for classification columns; changes recorded earlier
  become Unknown and need review.
- A Change Inbox page and `GET /api/projects/{projectId}/changes`, with
  category and review-status filters and tenant-scoped Project lookups.
- Optional OpenAI suggestions for Unknown changes, started per change from the
  Change Inbox or `POST /api/projects/{projectId}/changes/{changeId}/ai-classification`.
- Strict JSON Schema requests with `store: false` and a minimal pull request
  payload, through Spring's `RestClient` with no new dependency.
- Flyway migration `V5` for the classification source and explicit AI
  status, model, failure, and attempt time, with constraints that keep every
  AI suggestion in review.
- Stable AI error codes, safe stored failure messages, and a guard against
  calling OpenAI inside a database transaction.
- Human review of any change from the Change Inbox or
  `POST /api/projects/{projectId}/changes/{changeId}/review`, storing the
  confirmed category and breaking flag with the reviewer and time.
- A `reviewed` inbox filter, "Reviewed"/"Corrected by" card details, and a
  `HUMAN` classification source for corrected changes.
- Flyway migration `V6` for review columns, a same-tenant reviewer foreign
  key, and constraints that let breaking, Unknown, and AI-suggested changes
  leave review only through a recorded review.
- Draft Releases: one per Project, with a version, an optional summary, and
  hand-picked settled changes, through a Releases page and
  `/api/projects/{projectId}/releases` endpoints.
- A release note preview with a leading breaking-changes section, a fixed
  category order, and Conventional Commit prefixes removed.
- Flyway migration `V7` for releases and release membership, with one draft
  per Project, one release per change, and foreign keys that keep a change
  inside its own Project and tenant.
- Release publication from the draft page or
  `POST /api/projects/{projectId}/releases/{releaseId}/publish`, storing an
  immutable snapshot of sections and Markdown with the publisher and time.
- A read-only published release page with copyable Markdown, and a published
  releases list.
- Flyway migration `V8` for publication columns, `release_notes`,
  case-insensitive version uniqueness per Project, and triggers that reject
  changes to published releases, their changes, and their notes.
- Administrator and member roles; the registration account and existing
  `OWNER` accounts become administrators through Flyway migration `V9`.
- Single-use member invitations with hashed 256-bit tokens, a seven-day
  expiry, reissue and revoke, a Members page, and a three-step acceptance flow
  that keeps the token in the URL fragment.
- Administrator-only access to member management and GitHub repository
  configuration, enforced by URL rules and in the service.
- A two-stage `Dockerfile` that runs the application on a digest-pinned
  Temurin 21 JRE UBI minimal image as UID `65534`, with a health check on
  `GET /api/status`.
- `docker-compose.demo.yml` with the application and PostgreSQL 17, required
  secrets, a loopback-only application port, and an unpublished database
  port, plus a placeholder-only `.env.example`.
- GitHub Actions workflows for Conventional PR titles, actionlint, npm audit,
  Maven verification, CodeQL, dependency review, a full-history Gitleaks scan,
  and a Trivy image scan with a Compose smoke test.
- Checksum-verified installers for actionlint, Gitleaks, and Trivy, and weekly
  Dependabot updates targeting `develop`.

- Optional, write-only GitHub access tokens per repository, set by
  administrators on the Projects page or with
  `PUT /api/projects/{projectId}/github-integration/token`, checked against the
  repository's pull requests and stored with AES-256-GCM.
- A durable change processing queue: merged pull requests are recorded as
  Processing with a job, and a scheduled worker lists their changed files with
  `FOR UPDATE SKIP LOCKED` claims, bounded retries with backoff, and stale
  claim recovery.
- Typed review triggers for sensitive files and unavailable file lists, shown
  in the Change Inbox with the changed files and returned by the changes API.
- A configurable sensitive-path baseline (`RELEASEFLOW_SENSITIVE_PATHS`) and a
  documentation-only rule.
- Flyway migration `V10` for access tokens, processing state, changed files,
  review triggers, and `change_processing_jobs`, with constraints that keep
  Processing changes unsettled and triggered changes in review.
- Automatic AI classification in the change worker with OpenAI, Anthropic
  (official Java SDK, Structured Outputs), or DeepSeek, selected with
  `RELEASEFLOW_AI_PROVIDER`: one request per change, a neutral summary (what,
  why, technical detail, migration step) in the Organization's language, shown
  in the Change Inbox and returned as `neutralSummary`.
- A `CLASSIFIER_FALLBACK` review trigger for failed or unusable AI answers,
  stale-claim completion without a second AI request, and a manual retry.
- An Organization output language chosen at registration, shown on the
  Projects page, and changed by administrators or with
  `PUT /api/organization/output-language`.
- Flyway migration `V11` for the output language, neutral summaries, the AI
  provider, and the `CLASSIFYING` and `FALLBACK_REQUIRED` job states.
- A release review lifecycle (ADR-0010): `DRAFT -> IN_REVIEW -> APPROVED ->
  PUBLISHED`, with request-review, per-change `APPROVE`/`EDIT` decisions that
  review the change itself, rejection that removes a change during review,
  approval that records the approver, and return to draft.
- A planned release time per release, set at creation or with
  `PUT /api/projects/{projectId}/releases/{releaseId}/schedule`.
- `GET /api/projects/{projectId}/release-assignments`, listing the release of
  each assigned change.
- A release page with a four-step progress indicator, a breaking-change
  warning, review progress, Changes and Review tabs, and scheduling; status
  filters with counts on the Releases page.
- Error codes `release_status_conflict`, `release_review_incomplete`,
  `classification_changed`, and `invalid_release_schedule`.
- Flyway migration `V12` for the new statuses, schedule and approval columns,
  and `release_change_reviews`, with triggers that fix a release's changes and
  decisions once it is approved.
- Audiences (ADR-0011), managed by administrators on an Audiences page and
  through `/api/audiences`. Each has a fixed code, a display name, a
  communication intent, and a Mustache template validated on save. Three
  presets (operator, contributor, end user) are seeded in the output language
  at registration, and presets can be reset. An Organization keeps 1 to 20
  audiences.
- A narrative per audience in the single AI request, with a response schema
  built from the Organization's audience codes, stored as
  `audienceNarratives`.
- One release note per audience, written at approval as a digest: a "What's
  New" overview with counts, a breaking-change warning, fixed sections, and
  demoted item headings, with English or Vietnamese labels.
- Note editing while approved, which makes a note manual. Summary and
  narrative editing during review or after approval, which records the writer
  and renders the automatic notes again.
- Live per-audience previews, Copy and Download (`<version>-<code>.md`) for
  each note, and audience tabs on published releases.
- Server-side Markdown rendering with CommonMark that escapes HTML, sanitizes
  links, and turns images into links.
- REST endpoints for note previews, notes, note edits and downloads, and
  change summaries. Error codes `template_invalid`,
  `template_narratives_path`, `audience_code_taken`, `audience_limit`,
  `audience_last`, `audience_in_use`, `audience_not_preset`,
  `audience_not_found`, `release_note_render_failed`, `release_notes_missing`,
  and `release_note_not_found`.
- Sensitive paths per Project (ADR-0014):
  - administrators add glob patterns to the deployment's baseline on a
    Sensitive paths page and through
    `PUT /api/projects/{projectId}/sensitive-paths`; members read them;
  - the baseline can never be removed, and additions apply to changes
    classified afterwards;
  - at most 100 patterns of at most 256 characters, rejected with
    `invalid_sensitive_paths` when they do not compile.
- Flyway migration `V16` for `project_sensitive_paths`.
- Context sufficiency (ADR-0013):
  - an AI context score from 0 to 100 with reasons, lowered by fixed caps for
    empty descriptions and short or generic titles;
  - a `Not enough context` review trigger below `RELEASEFLOW_CONTEXT_THRESHOLD`
    (default 60);
  - scores in the Change Inbox and a `context=insufficient` filter.
- Possible duplicates (ADR-0013):
  - trigram and changed-file similarity against the Project's recent changes
    at or above `RELEASEFLOW_DUPLICATE_THRESHOLD` (default 0.82);
  - a `Possible duplicate` review trigger on the newer change;
  - evidence on both cards;
  - one-time Confirm or Not a duplicate decisions through the Change Inbox and
    `/api/projects/{projectId}/duplicate-candidates`.
- Error codes `duplicate_candidate_not_found` and `duplicate_candidate_decided`.
- Flyway migration `V15` for context columns on changes and
  `duplicate_candidates`.
- A category catalog per Organization (ADR-0012), managed by administrators
  on a Categories page and through `/api/categories`:
  - codes that are fixed once created, display names, and six groups;
  - archiving and restoring, with Unknown as the one system category;
  - the former fixed values seeded at registration and by `V14`.
- AI proposals of new categories when none fits:
  - an `AI proposed a new category` review trigger;
  - a gate where administrators add, map, or reject each proposal, from the
    Categories page, the Change Inbox, or `/api/category-suggestions`;
  - a change that takes the decided category keeps needing review.
- Error codes `category_code_taken`, `category_system`,
  `category_suggestion_decided`, `category_not_found`, and
  `category_suggestion_not_found`.
- Flyway migration `V14` for `category_definitions`, `category_suggestions`,
  category snapshots on changes, and the `SUGGESTION` classification source.
- Flyway migration `V13` for `audience_definitions` with seeded presets,
  narratives and summary authorship on changes, and `release_audience_notes`,
  with triggers that allow note writes only while a release is approved.

### Changed

- Categories come from the Organization's catalog instead of a fixed list. The
  rules pick the preferred category of their group, or the first active one in
  it. The AI chooses among active codes, and a code outside the catalog is an
  invalid answer. Reviews accept active codes in any case. Changes return
  `categoryName` and `categoryGroup` beside the `category` code, and release
  notes are sectioned by group.
- Release notes are written per audience at approval instead of once at
  publication. Publication freezes them, and the V8 `release_notes` table only
  keeps notes published before V13. Releases that were approved before V13
  return to review with their decisions kept.
- A published release returns `notes`; `markdown` and `preview` are filled
  only for releases published before audiences existed.
- A Project may prepare several releases at once, and a change that still
  needs review may join a draft; `409 draft_release_exists` is removed.
- Publishing requires an approved release; publishing a draft returns
  `409 release_status_conflict` instead of publishing it.
- A release can be scheduled or discarded until it is published.
- AI results may settle a change (ADR-0009, superseding ADR-0004), but never
  replace a category the rules chose, clear a breaking flag, or settle an
  Unknown or triggered change.
- AI configuration now starts with `RELEASEFLOW_AI_PROVIDER`;
  `RELEASEFLOW_OPENAI_TIMEOUT` is replaced by `RELEASEFLOW_AI_TIMEOUT`
  (default `PT60S`). OpenAI keys without a provider stop startup.
- The manual AI action is now a retry for failed attempts and for Unknown
  changes recorded before automatic AI.

- Classification now runs after the changed files are known instead of inside
  the webhook request; a change cannot be reviewed, sent to AI, or released
  while it is Processing (`409 change_processing` for reviews).

### Security

- Tomcat is pinned to 11.0.25 to fix CVE-2026-65182, CVE-2026-65905, and
  CVE-2026-68525 until the Spring Boot parent manages a fixed version.

### Fixed

- A blank registration password reports one required-field error instead of
  two.
- A rejected GitHub configuration keeps the submitted owner and repository.
- GitHub conflicts appear inside the submitted Project card; an unknown
  Project is reported at the top of the page.
- Project creation times display as a readable UTC date.
