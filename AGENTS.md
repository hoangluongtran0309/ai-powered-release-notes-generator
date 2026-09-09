# ReleaseFlow Contributor Guide

## Current state

ReleaseFlow currently implements only the bootstrap slice: one Spring Boot
application, a JSON status endpoint, a Thymeleaf home page, and smoke tests.
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
- Applied Flyway migrations are immutable.
- Published Release Notes are immutable snapshots.

## Commands

Use JDK 21.

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
```
