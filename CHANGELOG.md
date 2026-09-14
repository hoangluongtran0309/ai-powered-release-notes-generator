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

### Changed

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
