# ADR-0007: CI gates and container supply chain

- Status: Accepted
- Date: 2026-09-14

## Context

Until now every quality gate ran by hand before a pull request, and the only
way to run ReleaseFlow was a local JDK plus a separately managed PostgreSQL.
The repository is public, so pull requests, build tooling, and the image base
are all supply-chain inputs that an attacker can target. The gates therefore
have to be automatic, reproducible, and hard to tamper with. There is no
metrics or management endpoint yet, and no publication process.

## Decision

- GitHub Actions runs five workflows on pushes and pull requests to `develop`
  and `main`:
  - `CI`: Conventional PR title, actionlint, `npm ci` plus
    `npm audit --audit-level=high`, and `./mvnw clean verify` with PostgreSQL
    Testcontainers;
  - `CodeQL`: Java and JavaScript analysis, also weekly;
  - `Dependency Review`: pull requests that add a high or critical
    vulnerability fail;
  - `Secret Scan`: Gitleaks scans the complete Git history, also weekly;
  - `Container`: builds the image, fails on high or critical Trivy findings,
    and smoke tests the Compose stack.
- Every third-party action is pinned to a commit SHA, and every base image to a
  digest. Command-line tools (actionlint, Gitleaks, Trivy) are downloaded at a
  fixed version and checked against a SHA-256 checksum in `scripts/ci/`.
  Workflows default to `contents: read`, and checkouts do not keep
  credentials.
- Dependabot proposes weekly updates to `develop` for actions, Maven, npm,
  Dockerfile digests, and Compose digests.
- The image is built in two stages: the full JDK builds the release profile,
  and the application runs on the Temurin 21 JRE UBI minimal base as UID/GID
  `65534`. Its health check calls the public `GET /api/status` endpoint, because
  there is no private management port yet.
- `docker-compose.demo.yml` runs only the application and PostgreSQL. The
  database password and the credential master key have no default, so Compose
  refuses to start without them. The application is published on host
  loopback only, and the database port is not published.
- When a managed dependency has a fixed critical or high vulnerability that
  the Spring Boot parent does not yet include, the fixed version is pinned
  through the parent's version property, with a comment saying when to remove
  it.

## Consequences

- A vulnerable dependency or base image blocks merges until it is upgraded,
  even when the vulnerable code path is not used.
- Pinned SHAs and digests do not update themselves; Dependabot pull requests
  are the update path, and each one passes the same gates.
- The health check proves only that the web server answers. A future
  management port can check the database and replace it.
- No image is pushed to a registry, and no release artifact is produced.
