# ReleaseFlow Contributor Guide

## Current state

ReleaseFlow currently implements the bootstrap and Organization owner slices:
one Spring Boot application, PostgreSQL/Flyway V1, owner registration, session
authentication, tenant principal, REST/UI paths, and Testcontainers tests.
Read `README.md`, `docs/architecture.md`, and
`docs/implementation-status.md` before changing behavior.

## Working rules

- Implement one complete vertical slice at a time and stop for review.
- Keep code in one Maven module and package it by product capability.
- Add a dependency only when the current slice uses it.
- Keep REST and Thymeleaf controllers on the same application behavior.
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
- Applied Flyway migrations are immutable; add `V2` or later for schema changes.
- Published Release Notes are immutable snapshots.
- Tenant-owned repository lookups include both resource ID and the current
  principal's Organization ID.

## Commands

Use JDK 21. Docker must be running because persistence tests use PostgreSQL
Testcontainers; H2 is not permitted.

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
```
