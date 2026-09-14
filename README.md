# ReleaseFlow

ReleaseFlow is being rebuilt as a small modular monolith that turns software
changes into human-reviewed, immutable release notes. Development proceeds one
complete vertical slice at a time, and this repository documents only behavior
that is currently implemented.

## Current capability

The application currently provides:

- an atomic Organization and administrator registration flow through REST and
  Thymeleaf;
- canonical, globally unique account email addresses and BCrypt password hashes;
- administrator and member roles, with single-use, expiring member invitations;
- session authentication, CSRF protection, form login, and POST logout;
- an authenticated session endpoint whose tenant identity comes exclusively
  from the principal;
- PostgreSQL persistence managed by Flyway migrations `V1` through `V9`;
- tenant-scoped Project creation and listing through REST and Thymeleaf;
- one create-only GitHub repository integration per Project;
- a unique webhook identity and 256-bit signing secret for each integration;
- AES-256-GCM encryption at rest with one-time secret reveal;
- a signed GitHub webhook endpoint that records each merged pull request once
  as a normalized change, and shows the last verified delivery per repository;
- deterministic, explainable classification of every recorded change, with
  breaking and unrecognized changes always marked for human review;
- a per-Project Change Inbox in the UI and REST, filterable by category and
  review status;
- optional, person-initiated OpenAI suggestions for Unknown changes, which
  always stay in review;
- human review of any change, recording who confirmed or corrected its
  category and breaking flag;
- one Draft Release per Project, assembled from settled changes, with a live
  release note preview;
- publication of a draft as an immutable Release Note snapshot with copyable
  Markdown;
- `application/problem+json` responses with stable error codes for the current
  REST operations;
- the public home page and application status endpoint from the bootstrap
  slice;
- a Tailwind CSS, DaisyUI, and Alpine.js workspace UI with a light/dark theme;
- a non-root container image and a Docker Compose demo stack with PostgreSQL;
- GitHub Actions gates for tests, CodeQL, dependency review, secret scanning,
  and container vulnerability scanning.

Every slice in the current plan is implemented; external distribution and a
public changelog are deliberately deferred. See the
[implementation status](docs/implementation-status.md).

## Requirements

- JDK 21
- PostgreSQL for running the application
- Docker for the Testcontainers integration suite
- No system Maven installation; use the Maven Wrapper
- No system Node.js installation; the Maven build downloads a pinned Node.js
  and npm into `node/` on first use, so that build needs network access

## Run

Provide a dedicated PostgreSQL database, credentials, and a 256-bit credential
master key through the environment. Generate a new local key with
`openssl rand -base64 32`; do not commit or share the resulting value.

```bash
export RELEASEFLOW_DB_URL=jdbc:postgresql://localhost:5432/releaseflow
export RELEASEFLOW_DB_USERNAME=releaseflow
export RELEASEFLOW_DB_PASSWORD='replace-with-a-local-secret'
export RELEASEFLOW_CREDENTIAL_MASTER_KEY='replace-with-the-generated-base64-key'
./mvnw spring-boot:run
```

Set `RELEASEFLOW_SESSION_COOKIE_SECURE=true` whenever the application is served
over HTTPS. Open `http://localhost:8080/register` to create the first
organization administrator.

REST clients use the same session and CSRF policy as the server-rendered UI:

1. `GET /api/csrf` and retain the session cookie.
2. Send the returned token in the returned header when calling
   `POST /api/registrations`.
3. Sign in through `POST /login` using `email` and `password` form fields.
4. Read the authenticated identity from `GET /api/session`.
5. Create and list tenant-scoped Projects through `POST|GET /api/projects`.
6. Configure a repository with
   `POST /api/projects/{projectId}/github-integration`. Save the returned
   `webhookSecret` immediately; it is never returned again.

Authenticated users can perform the same workflow at `/projects`. GitHub owner
and repository names are canonicalized to lowercase. A repository can be
connected once per Organization, while different Organizations may connect the
same repository.

`GET /api/status` remains public.

## Run with Docker Compose

`docker-compose.demo.yml` builds the application image and runs it with
PostgreSQL 17 for local evaluation; it is not a production deployment.

```bash
cp .env.example .env
# Replace every REPLACE_ME value. Generate the master key with:
#   openssl rand -base64 32
docker compose -f docker-compose.demo.yml up --build
```

Compose refuses to start until `RELEASEFLOW_DB_PASSWORD` and
`RELEASEFLOW_CREDENTIAL_MASTER_KEY` are set. The application listens on
`http://127.0.0.1:8080` (change it with `RELEASEFLOW_HTTP_PORT`); the database
port is not published. The container runs as UID `65534`, and its health check
calls `GET /api/status`. `docker compose -f docker-compose.demo.yml down --volumes`
removes the demo data.

The `Dockerfile` can also be built on its own with `docker build .`. The image
reads the same `RELEASEFLOW_*` environment variables as `./mvnw spring-boot:run`.

## Members and invitations

The account created at registration is an **administrator**. Administrators
open **Members** to invite teammates by email, and only they can connect a
GitHub repository. Members can do everything else: review changes, request AI
suggestions, and prepare and publish releases.

An invitation link has the form `/accept-invite#token=…` and is shown once.
ReleaseFlow stores only a hash of it. The link expires after seven days (set
`RELEASEFLOW_INVITATION_TTL`, for example `P3D`, to change this), works once,
and can be reissued or revoked from the Members page. The invitee confirms the
organization, chooses a name and a password, and signs in as a member. Roles
cannot be changed and members cannot be removed yet.

REST clients use the same rules:

```text
GET    /api/members                         (administrator)
GET    /api/invitations                     (administrator)
POST   /api/invitations {"email"}           (administrator; 201, returns acceptancePath once)
POST   /api/invitations/{id}/reissue        (administrator)
DELETE /api/invitations/{id}                (administrator)
POST   /api/public/invitations/inspect {"token"}
POST   /api/public/invitations/accept  {"token", "displayName", "password"}
```

The public endpoints need a CSRF token from `GET /api/csrf`. Every unusable
token returns `400 invitation_invalid`. Other errors are `409
invitation_already_pending`, `409 invitation_email_unavailable` (the email
already has an account), `404 invitation_not_found`, and `403 access_denied`
for members calling administrator endpoints.

## Receive GitHub webhooks

In the GitHub repository, open **Settings → Webhooks → Add webhook** and enter:

- **Payload URL**: the public ReleaseFlow address followed by the webhook path
  shown for the Project, for example
  `https://releaseflow.example.com/webhooks/github/<webhook-id>`;
- **Content type**: `application/json`;
- **Secret**: the signing secret saved when the repository was connected;
- **Events**: *Let me select individual events* → *Pull requests*.

`POST /webhooks/github/{webhookId}` needs no session or CSRF token. ReleaseFlow
verifies `X-Hub-Signature-256` with that integration's own secret before it
reads the payload, and takes the Organization and Project from the integration,
never from the payload. It answers:

| Delivery | Response |
| --- | --- |
| Unknown webhook ID, missing or wrong signature | `401` `webhook_signature_invalid` |
| `repository.full_name` is not the configured repository | `422` `webhook_repository_mismatch` |
| Verified delivery with invalid JSON, no `X-GitHub-Event`, or a merged pull request missing fields or `X-GitHub-Delivery` | `400` `webhook_payload_malformed` |
| `ping` | `200` `{"outcome":"pong"}` |
| Merged pull request (`closed` with `merged: true`) | `200` `recorded`, or `duplicate` when already recorded |
| Any other event or action | `200` `ignored` |

Each merged pull request is stored once per Project, so GitHub redeliveries are
harmless. The Projects page shows the time of the last accepted delivery,
which appears as soon as GitHub sends its initial `ping`.

To exercise the endpoint locally without GitHub, sign the exact bytes you send:

```bash
WEBHOOK_PATH=/webhooks/github/replace-with-the-webhook-id
WEBHOOK_SECRET='replace-with-the-saved-secret'
BODY='{"zen":"Keep it logically awesome.","repository":{"full_name":"owner/repository"}}'
SIGNATURE="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $NF}')"
curl -sS -X POST "http://localhost:8080$WEBHOOK_PATH" \
  -H 'Content-Type: application/json' \
  -H 'X-GitHub-Event: ping' \
  -H "X-GitHub-Delivery: $(uuidgen)" \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  --data-binary "$BODY"
```

## Change Inbox

Every change is classified when its webhook delivery is recorded, using fixed
rules and no network call:

- a Conventional Commit type at the start of the pull request title (`feat`,
  `fix`, `perf`, `docs`, `refactor`, `chore`, `ci`, `build`, `test`, with an
  optional scope) selects Feature, Fix, Performance, Documentation, or
  Maintenance;
- without a title type, familiar labels such as `enhancement`, `bug`,
  `documentation`, or `dependencies` select the category, unless they disagree;
- a `!` after the title type, a `breaking-change` label, or a
  `BREAKING CHANGE:` footer in the description marks the change as breaking;
- anything else is Unknown.

Breaking and Unknown changes always need review. Each change keeps the list of
rules that matched, and the inbox shows it. Changes recorded before
classification existed are Unknown and need review.

Open `/changes` to browse a Project's inbox, or call
`GET /api/projects/{projectId}/changes`. Both accept `category`
(`feature`, `fix`, `performance`, `documentation`, `maintenance`, `unknown`) and
`status` (`needs-review`, `classified` for changes settled by rules without a
person, `reviewed`). An unsupported value returns
`400 invalid_change_filter`, and another Organization's Project returns
`404 project_not_found`.

## AI suggestions

AI is optional. To enable it, set both variables before starting the
application:

```bash
export RELEASEFLOW_OPENAI_API_KEY='replace-with-an-openai-api-key'
export RELEASEFLOW_OPENAI_MODEL='replace-with-a-model-that-supports-structured-outputs'
```

If only one of them is set, startup fails. If neither is set, the application
runs without AI. `RELEASEFLOW_OPENAI_BASE_URL` (default
`https://api.openai.com/v1`) and `RELEASEFLOW_OPENAI_TIMEOUT` (default `PT30S`)
are optional.

When AI is enabled, every Unknown change in the Change Inbox has a
**Classify with AI** button. REST clients call
`POST /api/projects/{projectId}/changes/{changeId}/ai-classification` with the
session and a CSRF token. ReleaseFlow sends the pull request title, labels,
target branch, and up to 4000 characters of its description, with
`store: false`. It does not send the author. The model's category, breaking
flag, and rationale are stored as an **AI suggestion**, and the change still
needs review.

| Outcome | REST response |
| --- | --- |
| Suggestion stored | `200` with the updated change |
| Change not in this Organization's Project | `404 change_not_found` |
| Change already classified by rules or AI | `409 change_not_eligible_for_ai` |
| OpenAI failed; the failure is stored and shown | `502 ai_classification_failed` |
| AI not configured | `503 ai_classification_unavailable` |

Nothing retries automatically. A person can press **Retry with AI** after a
failure.

## Human review

Every change in the Change Inbox has a review form prefilled with its current
category and breaking flag. Changes that need review show the form directly;
other changes show it under **Edit classification**. The reviewer keeps or
changes the values and presses **Confirm review**. ReleaseFlow stores exactly
the submitted values, clears the need for review, and records the reviewer and
time. A reviewer must choose a real category, since a reviewed change cannot
stay Unknown, and may clear a breaking flag set by the rules or AI.

Confirming without changes keeps the original classification source (rules or
AI). Changing the category or breaking flag makes the reviewer the source,
shown as "Corrected by". A change can be reviewed again; the latest review is
kept.

REST clients call `POST /api/projects/{projectId}/changes/{changeId}/review`
with the session, a CSRF token, and `{"category": "fix", "breaking": false}`.
It returns the updated change, `400 invalid_change_review` for `unknown` or an
unsupported category, `400 validation_failed` when a field is missing, and
`404 change_not_found` for another Organization's change.

## Draft Releases

Open **Releases** to prepare a Project's next release. A Project has at most
one draft at a time. A draft has a version (up to 50 characters) and an
optional summary. Only settled changes can be added, meaning changes that no
longer need review; each change belongs to at most one release. The draft
page lists the included changes, the available settled changes (with **Add
selected** and **Add all available**), and a preview of the release note:
breaking changes first, then Features, Fixes, Performance, Documentation, and
Maintenance, with Conventional Commit prefixes removed from titles.
Discarding a draft deletes it and makes its changes available again.

REST clients use the same rules:

```text
GET    /api/projects/{projectId}/releases
POST   /api/projects/{projectId}/releases                                {"version", "summary"}
GET    /api/projects/{projectId}/releases/{releaseId}
PUT    /api/projects/{projectId}/releases/{releaseId}                    {"version", "summary"}
DELETE /api/projects/{projectId}/releases/{releaseId}
GET    /api/projects/{projectId}/releases/{releaseId}/available-changes
POST   /api/projects/{projectId}/releases/{releaseId}/changes            {"changeIds": [...]} or {"allAvailable": true}
DELETE /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}
```

A second draft returns `409 draft_release_exists`. A change that still needs
review returns `409 change_not_releasable`. An unknown or foreign change
returns `404 change_not_found`, and an unknown or foreign release returns
`404 release_not_found`.

## Publishing

On a draft page, open **Publish release**. Publishing requires at least one
change and a version that no other release of the Project uses, ignoring case.
It stores an immutable snapshot of the release note as sections and Markdown,
together with who published it and when. The page then becomes read-only and
offers the Markdown with a **Copy** button for GitHub Releases or a changelog.
Published releases are listed on **Releases**, and a new draft can be started.

A published release cannot be edited, discarded, unpublished, or changed in
content. Its changes can still be corrected in the Change Inbox, but the
published note keeps what was published. PostgreSQL triggers enforce this even
for direct SQL. See
[ADR-0005](docs/adr/0005-immutable-release-note-snapshots.md).

REST clients call `POST /api/projects/{projectId}/releases/{releaseId}/publish`.
It returns the release with `status`, `publishedAt`, `publisherName`,
`markdown`, and the snapshot sections in `preview`. Errors:

| Situation | Response |
| --- | --- |
| The draft has no changes | `409 release_empty` |
| The version is already used in this Project | `409 release_version_taken` |
| The release is already published (publish, edit, discard, add, remove) | `409 release_published` |

## Verify

Docker must be available because persistence tests use PostgreSQL rather than
an in-memory substitute.

```bash
./mvnw test
./mvnw verify
```

While changing templates or styles, rebuild the stylesheet on every save in a
second terminal:

```bash
PATH="$PWD/node:$PATH" ./node/npm run watch
```

The application receives GitHub webhooks but never calls the GitHub API, so it
does not import history or validate repositories with GitHub. No image or
release artifact is published.

## Continuous integration

GitHub Actions runs these checks on every push and pull request to `develop`
and `main`:

| Workflow | Checks |
| --- | --- |
| `CI` | Conventional pull request title, actionlint, `npm audit --audit-level=high`, `./mvnw clean verify` |
| `CodeQL` | Java and JavaScript analysis (also weekly) |
| `Dependency Review` | Fails pull requests that add high or critical vulnerabilities |
| `Secret Scan` | Gitleaks over the complete Git history (also weekly) |
| `Container` | Image build, Trivy high/critical scan, Compose smoke test |

Actions are pinned to commit SHAs and images to digests; Dependabot proposes
weekly updates to `develop`. The pinned tools in `scripts/ci/` verify their
checksums, so the scans can be reproduced locally, for example:

```bash
./scripts/ci/install-gitleaks.sh "$HOME/.local/bin"
gitleaks git . --redact
```

See [ADR-0007](docs/adr/0007-ci-and-container-supply-chain.md).

## Contributing

Development uses `main` as the releasable branch, `develop` as the integration
branch, and short-lived branches for one reviewed change at a time. See the
[contributor guide](CONTRIBUTING.md) for branch and Conventional Commit rules.
