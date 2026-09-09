# ReleaseFlow

ReleaseFlow is being rebuilt as a small modular monolith that turns software
changes into human-reviewed, immutable release notes. Development proceeds one
complete vertical slice at a time, and this repository documents only behavior
that is currently implemented.

## Current capability

The bootstrap slice provides:

- a single Spring Boot application and executable JAR;
- `GET /`, a minimal server-rendered Thymeleaf page;
- `GET /api/status`, which returns the application name and `UP` status;
- smoke tests for application startup, REST, and server-rendered UI.

Organization tenancy, authentication, persistence, source integrations,
classification, review, and release publication are not implemented yet. See
[implementation status](docs/implementation-status.md).

## Requirements

- JDK 21
- No system Maven installation is required; use the Maven Wrapper.

## Run

```bash
./mvnw spring-boot:run
```

Open `http://localhost:8080/` or request the status endpoint:

```bash
curl http://localhost:8080/api/status
```

## Verify

```bash
./mvnw test
./mvnw verify
```

The application currently has no database, credentials, external services,
Node.js toolchain, container image, or published artifact.

## Contributing

Development uses `main` as the releasable branch, `develop` as the integration
branch, and short-lived branches for one reviewed change at a time. See the
[contributor guide](CONTRIBUTING.md) for branch and Conventional Commit rules.
