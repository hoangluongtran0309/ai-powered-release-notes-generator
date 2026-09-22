# Security policy

ReleaseFlow holds credentials people give it: webhook signing secrets, provider
access tokens, and the secrets an automation action delivers with. It also
answers unauthenticated webhooks. A weakness in any of that is worth reporting.

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting instead:

- go to [Security → Report a vulnerability][advisories], which opens a private
  draft advisory only you and the maintainer can read;
- or, if that is unavailable to you, email <hoangluongtran0309@gmail.com>.

Say what you found, how to reproduce it, and what an attacker could reach. A
proof of concept helps; a working exploit is not required and need not be shared
publicly at any point.

This is a personal project maintained by one person, so there is no paid triage
rota and no bounty. Expect a first reply within a week. If a report turns out to
be a real weakness, the fix, the advisory and the credit go out together.

[advisories]: https://github.com/hoangluongtran0309/ai-powered-release-notes-generator/security/advisories/new

## What is in scope

The application and its configuration: authentication and session handling,
tenant isolation between Organizations, webhook signature verification, secret
storage and the reveal-once rules, the outbound clients and their allowlists,
and the CSRF and security-header configuration.

## What is not

- The demo Compose stack (`docker-compose.demo.yml`), which exists to evaluate
  ReleaseFlow locally and says so; it is not a production deployment.
- A finding that needs an administrator's own credentials to reach something an
  administrator is already allowed to reach.
- A report from a scanner with no explanation of what it actually reaches.

## Supported versions

Only the current `main`. Nothing has been released yet, so there is no older
version to support.
