# ReleaseFlow Contributor Guide

## Current state

ReleaseFlow currently implements the bootstrap, Organization owner, Project
plus GitHub configuration, signed GitHub merged-pull-request intake,
deterministic classification with the Change Inbox, OpenAI classification,
human review, Draft Release, and Release Note publication slices: one Spring Boot
application, PostgreSQL/Flyway V1-V8, owner registration, session
authentication, tenant-scoped Projects, per-integration encrypted webhook
secrets, a signature-verified webhook endpoint that records normalized merged
pull requests idempotently, rule-based classification with mandatory review
for breaking and Unknown changes, a per-Project Change Inbox, optional
person-initiated OpenAI suggestions for Unknown changes that stay in review,
recorded human review of any change, one Draft Release per Project built from
settled changes, immutable published Release Note snapshots, REST/UI paths,
and Testcontainers tests.
Read `README.md`, `docs/architecture.md`, and
`docs/implementation-status.md` before changing behavior.

## Working rules

- Implement one complete vertical slice at a time and stop for review.
- Keep code in one Maven module and package it by product capability.
- Add a dependency only when the current slice uses it.
- Keep REST and Thymeleaf controllers on the same application behavior.
- Build pages from the Layout Dialect layouts with Tailwind and DaisyUI classes.
  Use Alpine.js only for presentation behavior; forms stay server-rendered.
  Never interpolate server data into Alpine expressions.
- Do not add an interface, event, worker, provider abstraction, or deployment
  component for a future use case.
- Keep developer documentation in English and aligned with runnable code.
- Update `README.md`, `CHANGELOG.md`, architecture, and implementation status
  in the same change as behavior.
- Follow the branch and Conventional Commit rules in `CONTRIBUTING.md`.
- Never claim that a tag, release, image, or artifact exists until it does.

## Product invariants for future slices

- Tenant identity comes from authenticated or cryptographically verified
  identity, never a submitted tenant field.
- Breaking changes require human review.
- External and AI failures become explicit unknown/review states.
- Network I/O does not run inside database transactions.
- Credentials never appear in source, logs, examples, or later API responses.
- `RELEASEFLOW_CREDENTIAL_MASTER_KEY` is required at startup and must decode to
  exactly 32 bytes; webhook secrets are reveal-once values.
- `RELEASEFLOW_OPENAI_API_KEY` and `RELEASEFLOW_OPENAI_MODEL` are optional but
  must be set together; AI output is a suggestion that always stays in review.
- Applied Flyway migrations are immutable; add `V2` or later for schema changes.
- Published Release Notes are immutable snapshots.
- Tenant-owned repository lookups include both resource ID and the current
  principal's Organization ID.

## Commands

Use JDK 21. Docker must be running because persistence tests use PostgreSQL
Testcontainers; H2 is not permitted.

Running the application also requires a Base64-encoded 32-byte value in
`RELEASEFLOW_CREDENTIAL_MASTER_KEY` in addition to the database environment
variables documented in `README.md`.

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
PATH="$PWD/node:$PATH" ./node/npm run watch
```

The Maven build installs Node.js and npm into `node/` and compiles
`static/css/application.css` into `target/classes`; do not commit `node/` or
`node_modules/`.
