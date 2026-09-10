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

## In progress

- Nothing. The Project and GitHub configuration slice and the workspace UI are
  complete and awaiting review.

## Planned

In intended implementation order:

1. Signed GitHub merged-pull-request intake and normalization.
2. Deterministic classification and the Change Inbox.
3. One OpenAI structured-classification integration.
4. Human review.
5. Draft Release management.
6. Immutable Release Note publication.

## Deliberately deferred

- Invitation and organization member management.
- Additional source and AI providers.
- Historical imports, polling, schedulers, queues, and automatic retries.
- GitHub access tokens, provider-side repository validation, integration
  replacement, and secret rotation.
- Dynamic audiences, localization, translation, and template engines.
- Automation, distribution integrations, and a public changelog.
- Client-rendered pages, JavaScript bundling and tests, containers, production
  observability, release automation, and an open-core/enterprise module split.
- Multi-repository aggregation.
