# Implementation Status

## Implemented

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint at `GET /api/status`.
- Server-rendered home page at `GET /`.
- Application-context, REST, and Thymeleaf smoke tests.

## In progress

- Nothing. The bootstrap slice is complete and awaiting review.

## Planned

In intended implementation order:

1. Organization owner registration, authentication, and tenant foundation.
2. Project and GitHub repository configuration.
3. Signed GitHub merged-pull-request intake and normalization.
4. Deterministic classification and the Change Inbox.
5. One OpenAI structured-classification integration.
6. Human review.
7. Draft Release management.
8. Immutable Release Note publication.

## Deliberately deferred

- Additional source and AI providers.
- Historical imports, polling, schedulers, queues, and automatic retries.
- Dynamic audiences, localization, translation, and template engines.
- Automation, distribution integrations, and a public changelog.
- Node.js frontend tooling, containers, production observability, release
  automation, and an open-core/enterprise module split.
- Multi-repository aggregation.
