# Implementation Status

## Implemented

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint and server-rendered home page.
- Organization plus administrator AppUser registration through REST and
  Thymeleaf.
- PostgreSQL persistence and Flyway `V1`, with Hibernate schema validation.
- Flyway `V2` for tenant-scoped Projects and GitHub integrations.
- Canonical unique emails, BCrypt password hashes, and atomic registration.
- Session authentication, tenant-bearing principal, form login, POST logout,
  and CSRF protection shared by REST and UI.
- Authenticated session identity and a public CSRF-token endpoint.
- Problem Details responses with stable codes for implemented API failures.
- Project creation/listing through shared REST and Thymeleaf application
  behavior.
- One canonical GitHub repository per Project, unique inside an Organization.
- Per-integration webhook IDs, one-time 256-bit secret reveal, and AES-256-GCM
  encrypted secret storage with tenant-bound authenticated data.
- PostgreSQL Testcontainers coverage including migration, constraints,
  cross-tenant lookup, negative security paths, REST, and UI.
- Workspace UI built with Tailwind CSS 4, DaisyUI 5, the Thymeleaf Layout
  Dialect, and Alpine.js, with a light/dark theme and a Maven-managed Node.js
  toolchain.
- Signed GitHub webhook intake: a sessionless endpoint per integration,
  constant-time HMAC-SHA256 verification before payload parsing, tenant
  identity from the verified integration, and repository identity checks.
- Flyway `V3` for normalized merged-pull-request changes, idempotent per
  Project and pull request number, and the last accepted delivery time.
- `ping` acknowledgement, ignored unrelated events, and a Projects page setup
  step showing the last verified delivery.
- Deterministic classification of each recorded change from its title type,
  labels, and `BREAKING CHANGE` footer, with explainable reasons.
- Mandatory review for breaking and Unknown changes, also enforced by a
  database constraint; Flyway `V4` marks earlier changes Unknown.
- A per-Project Change Inbox through Thymeleaf and REST with category and
  review-status filters.
- Optional automatic AI classification (ADR-0009) with OpenAI, Anthropic, or
  DeepSeek behind one `AiChangeClassifier` interface: one request per change in
  the worker, a shared JSON contract with a neutral summary, rules and review
  triggers that always win, explicit FAILED states with a fallback trigger, no
  automatic retry, stale-claim completion without a second call, a manual retry
  for failures and older Unknown changes, and Flyway `V5` and `V11`.
- An Organization output language (BCP 47) chosen at registration, changed by
  administrators through Thymeleaf and REST, and used for AI summaries.
- Human review of any change through Thymeleaf and REST: the reviewer confirms
  or corrects the category and breaking flag, and the reviewer and time are
  recorded. Flyway `V6` adds the review columns, a same-tenant reviewer foreign
  key, and constraints so breaking, Unknown, and AI-suggested changes leave
  review only through a recorded review.
- Draft Release management through Thymeleaf and REST: one draft per Project,
  version and summary, settled changes added by hand or all at once, removal,
  discard, and a grouped release note preview. Flyway `V7` enforces one draft
  per Project, one release per change, and same-Project, same-tenant
  membership.
- Immutable Release Note publication: a snapshot of sections and Markdown,
  the publisher and time, a read-only page with copyable Markdown, unique
  versions per Project, and Flyway `V8` triggers that reject any change to
  published releases, their changes, and their notes.

- Administrator and member roles with single-use, hashed, expiring member
  invitations through Thymeleaf and REST, a Members page, and Flyway `V9`.
- A two-stage, digest-pinned, non-root container image with a health check on
  `GET /api/status`, and a Docker Compose demo stack with PostgreSQL that
  requires its secrets to be supplied.
- GitHub Actions gates: Conventional PR titles, actionlint, npm audit, Maven
  verification, CodeQL, dependency review, a full-history Gitleaks scan, and
  a Trivy image scan with a Compose smoke test; weekly Dependabot updates.
- Optional, write-only GitHub access tokens set by administrators through
  Thymeleaf and REST, checked against the repository's pull requests before
  they are stored encrypted.
- A durable change processing queue: webhook intake records a `PROCESSING`
  change and a job; a scheduled worker claims jobs with `SKIP LOCKED`, lists
  changed files outside any transaction, retries transient GitHub failures,
  and recovers stale claims.
- Typed review triggers for sensitive paths and unavailable file lists, a
  configurable sensitive-path baseline, a documentation-only rule, and Flyway
  `V10` constraints that keep processing changes unsettled and triggered
  changes in review until a person reviews them.

## In progress

- Nothing. The automatic AI classification and output language slice is
  complete and awaiting review.

## Planned

Nothing is planned beyond the slices above. New work starts from the
deliberately deferred list below, one reviewed slice at a time.

## Deliberately deferred

- Role changes and member removal.
- Additional source providers, audience narratives, category suggestions,
  context sufficiency, and automatic AI retries.
- Historical imports, polling, and queues or retries for work other than
  changed-file collection.
- Validating a repository with GitHub when it is connected, per-Project
  sensitive-path additions, keyword review triggers, integration replacement,
  and secret or token rotation reminders.
- Dynamic audiences, localization, translation, and template engines.
- Automation, distribution integrations, and a public changelog.
- Client-rendered pages, JavaScript bundling and tests, browser end-to-end
  tests, production observability (Actuator, metrics, Prometheus, Grafana),
  image publication, release automation, and an open-core/enterprise module
  split.
- Multi-repository aggregation.
- Change Inbox pagination and search.
- Review history, comments, reviewer roles, and bulk review.
- Several concurrent drafts per Project, item reordering, and editing release
  note text.
- Unpublishing or correcting published release notes.
