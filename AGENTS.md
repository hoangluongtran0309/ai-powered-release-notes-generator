# ReleaseFlow Contributor Guide

## Current state

ReleaseFlow currently implements the bootstrap, Organization administrator, Project
plus GitHub configuration, signed GitHub merged-pull-request intake,
deterministic classification with the Change Inbox, automatic AI
classification with neutral summaries and an Organization output language,
human review, release review lifecycle, Release Note publication,
changed-file review, audience release note, category catalog, and review
signal slices: one Spring Boot application, PostgreSQL/Flyway V1-V15,
administrator
registration, member
invitations with administrator and member roles, session
authentication, tenant-scoped Projects, per-integration encrypted webhook
secrets, a signature-verified webhook endpoint that records normalized merged
pull requests idempotently, optional write-only GitHub access tokens, a durable
`SKIP LOCKED` worker that lists changed files outside transactions,
rule-based classification against a per-Organization category catalog with
mandatory review for breaking, Unknown, and sensitive-file changes, a
per-Project Change Inbox, optional automatic AI
classification (OpenAI, Anthropic, or DeepSeek) that the rules always override,
recorded human review of any change, releases that move from draft through a
per-change review and approval to publication (several per Project, with a
planned release time), administrator-managed audiences with Mustache templates
and AI narratives, one release note per audience written at approval and
frozen at publication, REST/UI paths, and Testcontainers tests. A non-root container image, a Docker Compose demo
stack, and GitHub Actions security and test gates are also in place.
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
- Invitation tokens are shown once and stored only as SHA-256 hashes.
- GitHub access tokens are write-only: only administrators set them, GitHub
  confirms them first, and no response, page, or log ever contains one.
- Review triggers only add a need for review; only a recorded human review
  clears it. Missing, refused, or incomplete changed-file lists force review.
- A `PROCESSING` change cannot be reviewed, sent to AI, or released.
- A release's changes are chosen while it is a draft; during review a change
  can only be rejected, which removes it. A release is approved only when every
  change has a decision, and published only from `APPROVED`. A decision reviews
  the change itself through `ChangeReviewService`.
- Background work uses its own job table, short claim and result
  transactions, and `FOR UPDATE SKIP LOCKED`; retries stay bounded.
- The sensitive-path list must compile and must not be empty.
- `RELEASEFLOW_CREDENTIAL_MASTER_KEY` is required at startup and must decode to
  exactly 32 bytes; webhook secrets are reveal-once values.
- AI is optional. `RELEASEFLOW_AI_PROVIDER` selects one provider, which needs
  its key and model; there is no default model. AI never replaces a category
  the rules chose, never clears a breaking flag, and never settles a change that
  is Unknown, triggered, or that it asked to have reviewed.
- Each change gets exactly one automatic AI request; a stalled `CLASSIFYING`
  job is completed without asking again, and a failure becomes a
  `CLASSIFIER_FALLBACK` trigger, never an automatic retry.
- AI failures store fixed, safe messages, never exception text or bodies.
- Applied Flyway migrations are immutable; add `V2` or later for schema changes.
- Published Release Notes are immutable snapshots.
- Categories come from the Organization's catalog, and a change keeps a
  snapshot of its category. A category the AI proposes is never added to the
  catalog without an administrator's decision, and a change that takes it still
  needs a person's review. The Unknown system category is never archived.
- Context sufficiency and possible duplicates are review evidence only: they add
  triggers but never settle, merge, or remove a change. Context is assessed only
  from an AI answer, and a duplicate decision is recorded once.
- An Organization always has at least one audience. Rendering a release note
  never calls AI; notes are written at approval from a snapshot of each
  audience's template and are frozen at publication.
- A summary or narrative written by a person is never replaced by the AI, and a
  note a person edited never follows its template again.
- Tenant-owned repository lookups include both resource ID and the current
  principal's Organization ID.
- GitHub Actions stay pinned to commit SHAs, images to digests, and downloaded
  CI tools to SHA-256 checksums. Compose secrets never get default values.

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
docker compose -f docker-compose.demo.yml up --build
```

The Compose stack reads `.env`; copy `.env.example` and replace every
placeholder first.

The Maven build installs Node.js and npm into `node/` and compiles
`static/css/application.css` into `target/classes`; do not commit `node/` or
`node_modules/`.
