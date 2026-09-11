# Implementation Status

## Implemented

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint and server-rendered home page.
- Organization plus OWNER AppUser registration through REST and Thymeleaf.
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
- One OpenAI structured-classification integration: person-initiated
  suggestions for Unknown changes, strict JSON Schema output, explicit
  FAILED states, no network call inside a transaction, and Flyway `V5`.
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

## In progress

- Nothing. The Release Note publication slice is complete and awaiting
  review.

## Planned

Nothing is planned beyond the slices above. New work starts from the
deliberately deferred list below, one reviewed slice at a time.

## Deliberately deferred

- Invitation and organization member management.
- Additional source and AI providers, automatic AI classification, and AI
  retries.
- Historical imports, polling, schedulers, queues, and automatic retries.
- GitHub access tokens, provider-side repository validation, integration
  replacement, and secret rotation.
- Dynamic audiences, localization, translation, and template engines.
- Automation, distribution integrations, and a public changelog.
- Client-rendered pages, JavaScript bundling and tests, containers, production
  observability, release automation, and an open-core/enterprise module split.
- Multi-repository aggregation.
- Change Inbox pagination and search.
- Review history, comments, reviewer roles, and bulk review.
- Several concurrent drafts per Project, item reordering, and editing release
  note text.
- Unpublishing or correcting published release notes.
