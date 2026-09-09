# Implementation Status

## Implemented

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint and server-rendered home page.
- Organization plus OWNER AppUser registration through REST and Thymeleaf.
- PostgreSQL persistence and Flyway `V1`, with Hibernate schema validation.
- Canonical unique emails, BCrypt password hashes, and atomic registration.
- Session authentication, tenant-bearing principal, form login, POST logout,
  and CSRF protection shared by REST and UI.
- Authenticated session identity and a public CSRF-token endpoint.
- Problem Details responses with stable codes for implemented API failures.
- PostgreSQL Testcontainers coverage including migration, constraints,
  cross-tenant lookup, negative security paths, REST, and UI.

## In progress

- Nothing. The Organization owner slice is complete and awaiting review.

## Planned

In intended implementation order:

1. Project and GitHub repository configuration.
2. Signed GitHub merged-pull-request intake and normalization.
3. Deterministic classification and the Change Inbox.
4. One OpenAI structured-classification integration.
5. Human review.
6. Draft Release management.
7. Immutable Release Note publication.

## Deliberately deferred

- Invitation and organization member management.
- Additional source and AI providers.
- Historical imports, polling, schedulers, queues, and automatic retries.
- Dynamic audiences, localization, translation, and template engines.
- Automation, distribution integrations, and a public changelog.
- Node.js frontend tooling, containers, production observability, release
  automation, and an open-core/enterprise module split.
- Multi-repository aggregation.
