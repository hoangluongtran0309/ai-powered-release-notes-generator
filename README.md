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
- PostgreSQL persistence managed by Flyway migrations `V1` through `V27`;
- tenant-scoped Project creation and listing through REST and Thymeleaf;
- create-only GitHub repository, GitLab project, Linear team, and Jira Cloud
  project sources, several per Project;
- a unique webhook identity and 256-bit signing secret for each integration;
- AES-256-GCM encryption at rest with one-time secret reveal;
- signed GitHub, GitLab, and Linear webhook endpoints that record each merged
  pull request, merged merge request, and completed issue once as a normalized
  change, and show the last verified delivery per source;
- a Jira project read every five minutes for issues that moved into Done, with
  no webhook to set up;
- Jira issues mentioned by a pull or merge request's title, description, branch,
  or commits, shown on the change, given to the AI, and available to release
  note templates;
- an optional, write-only access token per source, confirmed with the provider
  and stored encrypted;
- a durable processing queue that asks each change's provider for what it can
  add — the files it touched, or the issue restated — outside the webhook
  request, with bounded retries;
- deterministic, explainable classification of every recorded change, with
  breaking, unrecognized, and sensitive-file changes always marked for human
  review, and sensitive-path patterns that administrators can add per Project;
- a per-Project Change Inbox in the UI and REST, filterable by category and
  review status;
- a category catalog per Organization, managed by administrators, with
  categories the AI proposes held for an administrator's decision;
- review signals for pull requests that give too little context and for
  changes that look like earlier ones, with the evidence shown to reviewers;
- optional automatic AI classification with OpenAI, Anthropic, or DeepSeek:
  one request per change, a neutral summary and a narrative for each audience
  in the Organization's output language, and rules and review triggers that
  always win;
- audiences managed by administrators, each with a communication intent and a
  Mustache template; three presets (operator, contributor, end user) are
  created with every Organization;
- an Organization output language, chosen at registration and changed by
  administrators;
- human review of any change, recording who confirmed or corrected its
  category and breaking flag;
- releases that move from draft through a per-change review and approval to
  publication, with several drafts per Project, a planned release time, and a
  live preview of each audience's note;
- one release note per audience written at approval, editable until
  publication, with editable change summaries that update the notes;
- release notes in up to five languages, with each change translated by DeepL
  through a durable, cached queue, per-language audience templates, and
  publication held until every note is ready;
- publication of an approved release that freezes every audience's note, with
  copyable and downloadable Markdown;
- `application/problem+json` responses with stable error codes for the current
  REST operations;
- the public home page and application status endpoint from the bootstrap
  slice;
- a Tailwind CSS, DaisyUI, and Alpine.js workspace UI with a light/dark theme;
- an English or Vietnamese interface, chosen per person, with every page,
  form message, and error explanation written in the reader's own language;
- a workspace overview with four figures and the one next step that is
  unfinished, a Project switcher the server remembers, error pages for a
  mistyped or forbidden address, and tables that become cards on a phone;
- a non-root container image and a Docker Compose demo stack with PostgreSQL,
  Prometheus, and Grafana, with metrics on a private management port;
- automation rules that deliver a published release note to a GitHub Release, a
  Slack channel, a list of email addresses, a Notion page, a Confluence Cloud
  space, a Microsoft Teams chat, or a Zendesk help centre, in an order the rule
  fixes, with a
  durable run history, an outcome nobody may repeat without confirming it, and no
  way for a rule to hold up the release that triggered it;
- GitHub Actions gates for tests, CodeQL, dependency review, secret scanning,
  and container vulnerability scanning.

Every slice in the current plan is implemented. What is deliberately left out
is listed in the [implementation status](docs/implementation-status.md).

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

Email automation also needs an SMTP server and a sender address:

```bash
export RELEASEFLOW_SMTP_HOST=smtp.example.com
export RELEASEFLOW_SMTP_PORT=587
export RELEASEFLOW_SMTP_USERNAME=releaseflow
export RELEASEFLOW_SMTP_PASSWORD='replace-with-a-local-secret'
export RELEASEFLOW_SMTP_AUTH=true
export RELEASEFLOW_SMTP_STARTTLS=true
export RELEASEFLOW_AUTOMATION_EMAIL_FROM='releases@example.com'
```

Without them, an email action simply cannot be enabled; the rest of automation
works. `RELEASEFLOW_AUTOMATION_TIMEOUT` (default `PT10S`) bounds every delivery
that leaves the application, and `RELEASEFLOW_SLACK_ALLOWED_HOSTS` (default
`hooks.slack.com,hooks.slack-gov.com`) is the only set of origins a Slack webhook
URL may name.

A Notion, Confluence, Microsoft Teams, or Zendesk action needs nothing from the
deployment beyond the defaults: its token, callback URL, or client secret is
entered per action in the UI.
`RELEASEFLOW_NOTION_API_BASE_URL` (default `https://api.notion.com`) and
`RELEASEFLOW_NOTION_VERSION` (default `2026-03-11`) pin the API a page is written
against; change the version only together with the body the application sends.
`RELEASEFLOW_CONFLUENCE_API_BASE_URL` is empty by default, which means each action
calls its own site; set it only to stand in for Confluence Cloud locally, and note
that the site is still checked and every recorded link still points at it.
`RELEASEFLOW_TEAMS_API_BASE_URL` and `RELEASEFLOW_ZENDESK_API_BASE_URL` do the same
for those two; the Teams stand-in keeps the callback's path and signature, and a
Zendesk link is always the one Zendesk itself returned.

Rules that fire on their own, and rules other systems call, have their own
settings:

| Variable | Default | What it does |
| --- | --- | --- |
| `RELEASEFLOW_AUTOMATION_TRIGGER_DELAY` | `PT15S` | How often ReleaseFlow looks for a schedule or a reminder that has come due |
| `RELEASEFLOW_AUTOMATION_TRIGGER_BATCH_SIZE` | `20` | How many firings one of those passes may book |
| `RELEASEFLOW_AUTOMATION_REMINDER_LOOKAHEAD` | `PT15S` | How far ahead a reminder with no notice at all looks |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_CLOCK_SKEW` | `PT5M` | How far a signed call's timestamp may be from now |
| `RELEASEFLOW_AUTOMATION_WEBHOOK_MAX_BODY_BYTES` | `65536` | The largest signed call body, deliberately far below the limit a provider's own delivery gets |

Set `RELEASEFLOW_SESSION_COOKIE_SECURE=true` whenever the application is served
over HTTPS. Open `http://localhost:8080/register` to create the first
organization administrator.

REST clients use the same session and CSRF policy as the server-rendered UI.
Every write needs a token, and CSRF protection is never turned off: the only
paths excused are the signed webhook endpoints under `/webhooks/`, which carry
no cookie and prove themselves with an HMAC over the request instead. See
[ADR-0023](docs/adr/0023-scoped-csrf-exemptions.md).

1. `GET /api/csrf` and retain the session cookie.
2. Send the returned token in the returned header when calling
   `POST /api/registrations`.
3. Sign in through `POST /login` using `email` and `password` form fields.
4. Read the authenticated identity from `GET /api/session`.
5. Create and list tenant-scoped Projects through `POST|GET /api/projects`.
6. Connect a source with `POST /api/projects/{projectId}/sources`:
   `{"type": "GITHUB", "owner", "repository"}`,
   `{"type": "GITLAB", "apiBaseUrl", "projectPath", "webhookAuthMode"}`, or
   `{"type": "LINEAR", "teamId", "webhookSecret", "apiToken"}`, or
   `{"type": "JIRA", "siteUrl", "projectKey", "accountEmail", "apiToken"}`. For
   GitHub and GitLab, save the returned `webhookSecret` immediately; it is never
   returned again. Linear makes its own secret, so you supply it and nothing is
   returned. Jira has no webhook at all, so nothing is returned either.
   A Project may have several sources; `GET /api/projects/{projectId}/sources`
   lists them.
7. Optionally set a source's access token with
   `PUT /api/projects/{projectId}/sources/{sourceId}/token` (see
   [Access tokens](#access-tokens)).
8. Optionally write automation rules through `/api/automation/rules` (see
   [Automation](#automation)).

Authenticated users can perform the same workflow at `/projects`. Repository
owners, repository names, and GitLab project paths are canonicalized to
lowercase. A repository or project can be connected to one Project per
Organization, while different Organizations may connect the same one, and the
same name on the two providers is two separate sources. Connecting a source and
setting its token are for administrators; every member can list sources. An
unknown source returns `404 source_not_found`, and one already connected returns
`409 github_repository_already_connected`,
`409 gitlab_project_already_connected`, `409 linear_team_already_connected`, or
`409 jira_project_already_connected`.

`GET /api/status` remains public.

## Run with Docker Compose

`docker-compose.demo.yml` builds the application image and runs it with
PostgreSQL 17, Prometheus, and Grafana for local evaluation; it is not a
production deployment.

```bash
cp .env.example .env
# Replace every REPLACE_ME value. Generate the master key with:
#   openssl rand -base64 32
docker compose -f docker-compose.demo.yml up --build
```

Compose refuses to start until `RELEASEFLOW_DB_PASSWORD`,
`RELEASEFLOW_CREDENTIAL_MASTER_KEY`, and `RELEASEFLOW_GRAFANA_PASSWORD` are set.
The application listens on `http://127.0.0.1:8080` (change it with
`RELEASEFLOW_HTTP_PORT`), the operations dashboard on `http://127.0.0.1:3000`
and Prometheus on `http://127.0.0.1:9090`. The database port and the
management port are not published. The container runs as UID `65534`, and its
health check calls the management port.
`docker compose -f docker-compose.demo.yml down --volumes` removes the demo data.

## Watch a deployment

Actuator answers on its own port — 8081 on loopback by default, set with
`RELEASEFLOW_MANAGEMENT_PORT` and `RELEASEFLOW_MANAGEMENT_ADDRESS` — and serves
exactly two things there: `GET /actuator/health`, without component details, and
`GET /actuator/prometheus`. Everything else is refused, and the application port
serves none of it. Keep that port private; it is not a product endpoint.

Four metrics answer the operational questions, and every label comes from a
finite set:

| Metric | Labels | What it answers |
| --- | --- | --- |
| `releaseflow_classification_completed_total` | `needs_human_review` | How much is classified, and how much of it still needs a person |
| `releaseflow_classification_collect_to_complete_seconds` | — | How long a change waits between arriving and being classified |
| `releaseflow_classification_provider_requests_total` | `provider`, `outcome` | How often the AI refuses or cannot be read |
| `releaseflow_automation_action_executions_total` | `trigger`, `outcome` | How deliveries end, with unknown kept apart from failed |

**No label ever carries an Organization, Project, release, rule, run, action,
model, external reference, or error text.** Metrics are a deployment-wide view; a
question about one Organization is a product feature, not a label. Every series
exists from startup at zero, so a panel reads zero rather than "no data".

The demo stack provisions a *ReleaseFlow Operations* dashboard in Grafana with
seven panels over those metrics, and keeps seven days of history. Alerting,
durable storage, a retention policy, and access control are a real deployment's
own to bring. See [ADR-0026](docs/adr/0026-deployment-observability.md).

The `Dockerfile` can also be built on its own with `docker build .`. The image
reads the same `RELEASEFLOW_*` environment variables as `./mvnw spring-boot:run`.

## Members and invitations

The account created at registration is an **administrator**. Administrators
open **Members** to invite teammates by email, and only they can connect a
GitHub repository or set its access token. Members can do everything else: review changes, request AI
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

Each merged pull request is stored once per repository, so GitHub redeliveries
are harmless, and two repositories of one Project may both have a pull request
#12. The Projects page shows the time of the last accepted delivery,
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

## Receive GitLab webhooks

In the GitLab project, open **Settings → Webhooks → Add new webhook** and enter:

- **URL**: the public ReleaseFlow address followed by the webhook path shown for
  the source, for example
  `https://releaseflow.example.com/webhooks/gitlab/<webhook-id>`;
- **Secret token**: the secret saved when the project was connected;
- **Trigger**: *Merge request events*.

`POST /webhooks/gitlab/{webhookId}` needs no session or CSRF token, and
ReleaseFlow proves the delivery with that source's own secret before it reads
the payload. Which check applies is chosen when the source is connected:

- **Signed headers (Standard Webhooks)**, the default: `webhook-id`,
  `webhook-timestamp`, and `webhook-signature` must all be present, the
  signature must be `v1,` followed by the Base64 HMAC-SHA256 of
  `id.timestamp.body` under the secret — any one of several space-separated
  candidates may match — and the timestamp must be within five minutes of
  ReleaseFlow's clock.
- **Secret token header**: `X-Gitlab-Token` must equal the secret.

The Organization and Project come from the source, never from the payload:

| Delivery | Response |
| --- | --- |
| Larger than 1 MiB | `413` `webhook_payload_too_large` |
| Unknown webhook ID, a source of another type, missing, stale, or wrong proof | `401` `webhook_signature_invalid` |
| `project.path_with_namespace` is not the connected project | `422` `webhook_repository_mismatch` |
| Proven delivery with invalid JSON, or a merged merge request missing fields | `400` `webhook_payload_malformed` |
| Merged merge request (`object_kind: merge_request` with `action: merge`) | `200` `recorded`, or `duplicate` when already recorded |
| Any other event or action | `200` `ignored` |

Each merge request is stored once per source, so redeliveries are harmless.
GitLab identifies a delivery only in its signing mode, so a change records
`X-Gitlab-Event-UUID` when it is a GUID and nothing otherwise.

Only instances named in `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` (default
`gitlab.com`) can be connected. An entry is `host` or `host:port`, which means
HTTPS, or a full origin such as `http://gitlab.internal:8080`; writing the
scheme is the only way to accept an instance reached without TLS. A URL with
credentials, a query, or a fragment is refused with
`400 gitlab_base_url_invalid`, and any other host with
`400 gitlab_host_not_allowed`. ReleaseFlow never follows a redirect from a
GitLab instance.

To exercise the endpoint locally without GitLab, sign the exact bytes you send:

```bash
WEBHOOK_PATH=/webhooks/gitlab/replace-with-the-webhook-id
WEBHOOK_SECRET='replace-with-the-saved-secret'
BODY='{"object_kind":"push"}'
DELIVERY_ID="$(uuidgen)"
TIMESTAMP="$(date +%s)"
SIGNATURE="v1,$(printf '%s' "$DELIVERY_ID.$TIMESTAMP.$BODY" \
  | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$(printf '%s' "$WEBHOOK_SECRET" \
      | basenc --base64url -d 2>/dev/null | xxd -p -c 256)" -binary | base64)"
curl -sS -X POST "http://localhost:8080$WEBHOOK_PATH" \
  -H 'Content-Type: application/json' \
  -H "webhook-id: $DELIVERY_ID" \
  -H "webhook-timestamp: $TIMESTAMP" \
  -H "webhook-signature: $SIGNATURE" \
  --data-binary "$BODY"
```

## Receive Linear webhooks

Linear mints the signing secret itself, so create the webhook there first: under
**Settings → API → Webhooks**, set the URL to your ReleaseFlow address followed
by the path ReleaseFlow shows for the source, and enable the **Issues** event.
Copy the secret Linear displays and paste it, with an API key, when connecting
the team in ReleaseFlow.

`POST /webhooks/linear/{webhookId}` needs no session or CSRF token. A delivery
proves itself with four things, all of which must hold:

- a `Linear-Delivery` header;
- a `createdAt` within `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` (60 seconds) of
  ReleaseFlow's clock, in ISO-8601 or as epoch seconds or milliseconds;
- an `organizationId` equal to the workspace the team belongs to, which
  ReleaseFlow learned when the team was connected;
- a hex HMAC-SHA256 of the raw body in `Linear-Signature`.

| Delivery | Response |
| --- | --- |
| Larger than 1 MiB | `413` `webhook_payload_too_large` |
| Unknown webhook ID, a source of another type, a missing header, a stale stamp, another workspace, or a wrong signature | `401` `webhook_signature_invalid` |
| An issue of a team other than the connected one | `422` `webhook_repository_mismatch` |
| Proven delivery with invalid JSON, or an issue missing fields | `400` `webhook_payload_malformed` |
| An issue that has just moved into a completed state | `200` `recorded`, or `duplicate` when already recorded |
| Any other event, action, or state change | `200` `ignored` |

An issue counts only when it really moved into being done: `type` `Issue`,
`action` `update`, an `updatedFrom` that carries a state, a new state of
`completed`, and an old state that is not. Editing an issue that was already
completed changes nothing.

Linear names no delivery ReleaseFlow can record as a GUID, so a Linear change
carries no delivery ID. Each issue is still stored once per source, by its UUID,
so redeliveries are harmless.

## Poll Jira

Jira needs nothing configured on its side. Connect a Jira Cloud project with its
site (`https://your-site.atlassian.net`), its project key (`APP`), the email of
the account ReleaseFlow signs in as, and an
[Atlassian API token](https://id.atlassian.com/manage-profile/security/api-tokens)
for that account that can browse the project. ReleaseFlow asks Jira to confirm
them before storing anything, and answers `400 jira_site_invalid` for a site that
is not an HTTPS `atlassian.net` address with no port, credentials, query, or
fragment, `400 jira_token_rejected` when Jira refuses the account and token, and
`503 jira_unavailable` when it cannot be reached.

From then on, ReleaseFlow reads the project every
`RELEASEFLOW_JIRA_POLL_INTERVAL` (five minutes). Each read asks for the issues in
a Done status category that were updated since the previous read, reaching back
`RELEASEFLOW_JIRA_POLL_OVERLAP` (ten minutes) so nothing at the edge is lost, and
records each issue that was resolved in that window once. Issues resolved before
the project was connected are never read. A change from Jira:

- is numbered after its key — `APP-123` is `#123` — and titled
  `APP-123: summary`, with the reporter as its author and
  `{site}/browse/APP-123` as its link;
- has no changed files, so it is checked with the same sensitive-word scan as a
  Linear issue;
- starts with its key, so the Conventional Commit title rule never matches it:
  without AI, a Jira change is Unknown and needs review.

A read that fails keeps its place and tries again, and the source shows
**Connection failing** until one succeeds. The Change Inbox shows each Jira
project's last and next read; a Jira project has no history import.

### Linked Jira issues

When a Project has a Jira source, each GitHub or GitLab change is checked for
that project's issue keys in its title, description, target branch, and up to
250 commit messages. At most ten issues are read from Jira and shown under
**Linked issues** on the change, given to the AI as untrusted evidence, and
available to audience templates:

```mustache
{{#linkedIssues}} [{{key}}]({{url}}) {{title}} ({{type}}, {{status}}){{/linkedIssues}}
```

Commit messages are only searched for keys; they are never stored. When commits
could not be read, or Jira would not show an issue the change mentions, the
change is retried and then marked **Linked issues incomplete**, which forces
review. A key followed by a hyphen, as in `feature/APP-3-export`, is not taken
as a mention.

## Access tokens



With an access token, ReleaseFlow lists the files each merged pull or merge
request touched, so changes to sensitive files are always reviewed. Without one,
every new change needs review, because nothing can be ruled out.

For GitHub, create a fine-grained personal access token limited to the
repository, with **Pull requests: Read-only** permission (a classic token needs
`repo` for a private repository). For GitLab, create a project access token with
the `read_api` scope. For Linear, create an API key that can read the team —
that one is required when the team is connected, because confirming it is also
how ReleaseFlow learns the workspace. For Jira, the API token is required too,
and always belongs to the account email the source was connected with. An
administrator enters it under the
source in the Project's card on the Projects page, or calls:

```text
PUT /api/projects/{projectId}/sources/{sourceId}/token {"token"}   (administrator; 204)
```

ReleaseFlow first asks the provider to confirm the token — GitHub for the
repository's pull requests, GitLab for the project, Linear for the team, which it
accepts only while the key still reaches the same workspace, Jira for the project
— then stores it
encrypted with AES-256-GCM. The token is never returned; the source only reports
`accessTokenConfigured` and `accessTokenUpdatedAt`. Sending a new token replaces
the old one. Errors are `400 github_token_rejected` or `400 gitlab_token_rejected`
or `400 linear_token_rejected` or `400 jira_token_rejected` when the provider
refuses the token, `503 github_unavailable`, `503 gitlab_unavailable`, or
`503 jira_unavailable` when it cannot be reached,
`404 source_not_found`, and `403` for members.

GitLab's changed files come from the merge request's diffs, at most ten pages of
a hundred. A diff GitLab reports as collapsed, too large, or truncated makes the
whole list unavailable rather than short, so a file that was never seen is never
declared safe.

Linear has no changed files at all, which is not the same as failing to list
them: instead of forcing review for ever, ReleaseFlow reads the issue's own title
and description for `breaking change`, `migration`, `security`, `auth`,
`credential`, `password`, and `encryption`, and each match becomes a
**Sensitive word** trigger that forces review. Linear's token is also what lets
ReleaseFlow read the issue back, so a change carries the issue's current wording
rather than whatever it said the instant it was completed.

## History import

Webhooks bring in the pull and merge requests merged after a source is
connected. To add the ones merged before, an administrator opens the **Source
sync** panel of the Change Inbox and chooses **Import last 90 days** for a
source with an access token, or calls the REST endpoints below. Only a source
whose provider keeps a history ReleaseFlow can read offers it; Linear and Jira
do not, and asking anyway answers `409 source_import_not_supported`. A background
worker reads the source's history 100 items per page — GitHub's closed pull
requests, most recently updated first, or GitLab's merged merge requests updated
after the window's start, least recently updated first — and records every one
merged in the last 90 days through the same processing as a webhook delivery:
its changed files are listed and, when AI is configured, it is classified once.
An item already recorded, by webhook or by an earlier import, is skipped, so
imports and webhooks never duplicate each other. Imported changes carry an
**Imported** badge.

A GitHub import stops at the first pull request updated before its window, and
either import stops after 500 new changes as **Stopped at the limit** with its
position saved; **Resume** continues from there with a fresh count, and so does
resuming a failed import. When the provider rate-limits the import, it waits as
long as the provider asks (`Retry-After` or the rate-limit reset, at most an
hour); other temporary failures wait with growing delays. After five attempts,
or at once when the provider refuses the token, the import fails, and a refused
token marks the source as **Connection failing** until a new token is saved.
Only one import of a source runs at a time.

```text
GET  /api/projects/{projectId}/imports                              (every member)
POST /api/projects/{projectId}/sources/{sourceId}/imports           (administrator; 202)
POST /api/projects/{projectId}/sources/{sourceId}/imports/resume    (administrator; 202)
```

Each import reports `status` (`PENDING`, `RUNNING`, `RETRY_SCHEDULED`,
`COMPLETED`, `PARTIAL`, or `FAILED`), `scannedCount`, `importedCount`, the
window, `lastSyncAt`, `lastErrorCode`, and `canResumeImport`. A polled source
reports `polled` and `nextPollAt`, and its latest job is its latest poll. Errors are
`409 source_token_missing`, `409 source_sync_in_progress`,
`409 source_import_not_resumable`, and `404 source_not_found`. See
[ADR-0016](docs/adr/0016-integration-sources-and-history-import.md).

See also [ADR-0017](docs/adr/0017-gitlab-sources.md) for the GitLab source and
[ADR-0018](docs/adr/0018-linear-sources.md) for the Linear source, which has no
history to import, and [ADR-0019](docs/adr/0019-jira-sources-and-linked-context.md)
for Jira polling and linked issues.

These provider settings are optional:

| Variable | Default | Meaning |
| --- | --- | --- |
| `RELEASEFLOW_GITHUB_API_BASE_URL` | `https://api.github.com` | Where GitHub is called |
| `RELEASEFLOW_GITHUB_TIMEOUT` | `PT5S` | Connect and read timeout for GitHub |
| `RELEASEFLOW_GITLAB_ALLOWED_HOSTS` | `gitlab.com` | The GitLab instances a source may name |
| `RELEASEFLOW_GITLAB_TIMEOUT` | `PT5S` | Connect and read timeout for GitLab |
| `RELEASEFLOW_GITLAB_WEBHOOK_CLOCK_SKEW` | `PT5M` | How stale a signed GitLab delivery may be |
| `RELEASEFLOW_LINEAR_API_BASE_URL` | `https://api.linear.app` | Where Linear's GraphQL API is called |
| `RELEASEFLOW_LINEAR_TIMEOUT` | `PT5S` | Connect and read timeout for Linear |
| `RELEASEFLOW_LINEAR_WEBHOOK_CLOCK_SKEW` | `PT60S` | How stale a signed Linear delivery may be |
| `RELEASEFLOW_JIRA_TIMEOUT` | `PT10S` | Connect and read timeout for Jira |
| `RELEASEFLOW_JIRA_POLL_INTERVAL` | `PT5M` | How often each Jira project is read |
| `RELEASEFLOW_JIRA_POLL_OVERLAP` | `PT10M` | How far each read reaches back before the previous one |
| `RELEASEFLOW_JIRA_DESCRIPTION_MAX_CHARACTERS` | `8000` | The longest Jira description kept as evidence |
| `RELEASEFLOW_JIRA_API_BASE_URL` | (empty) | Local testing only: send every Jira call here instead of the site |
| `RELEASEFLOW_WEBHOOK_MAX_BODY_BYTES` | `1048576` | The largest delivery any provider may send |

## Change Inbox

A recorded change first shows as **Processing**. Within about a second, a
background worker lists its changed files with the Project's access token
(fewer than 500 files), outside any database transaction, and then classifies it.
If GitHub is unreachable or rate limited, the worker tries up to three times,
waiting 2 and then 4 seconds; if the files still cannot be listed, if the
token is missing or refused, or if the pull request changes 500 files or
more, the change is classified anyway with a **Changed files unavailable**
review trigger. A Processing change cannot be reviewed, sent to AI, or added
to a release. Work survives restarts, and a claim left by a stopped worker is
taken over after ten minutes.

Classification uses fixed rules:

- a Conventional Commit type at the start of the pull request title (`feat`,
  `fix`, `perf`, `docs`, `refactor`, `chore`, `ci`, `build`, `test`, with an
  optional scope) selects Feature, Fix, Performance, Documentation, or
  Maintenance (see [Categories](#categories) for how a rule picks a category
  from the catalog);
- without a title type, familiar labels such as `enhancement`, `bug`,
  `documentation`, or `dependencies` select the category, unless they disagree;
- when every changed file is in `docs/` or ends in `.md`, `.adoc`, or `.rst`,
  the change is Documentation, whatever its title says;
- a `!` after the title type, a `breaking-change` label, or a
  `BREAKING CHANGE:` footer in the description marks the change as breaking;
- anything else is Unknown.

Breaking and Unknown changes always need review, and so does any change with a
review trigger: a changed file (or the previous path of a renamed file) that
matches a sensitive-path pattern, or changed files that could not be listed.
Each change keeps the rules that matched, its typed `reviewTriggers`, and its
`changedFiles`, and the inbox shows them. A review settles a triggered change
but keeps the trigger as a record. Changes recorded before classification
existed are Unknown and need review; changes recorded before changed-file
collection keep their classification.

Sensitive paths are JDK glob patterns. The default list covers `security` and
`auth` directories, files named like `*Security*`, `*Auth*`, `*Credential*`,
or `*Password*`, database migrations and `*.sql`, `pom.xml`, `package.json`,
`package-lock.json`, `.github/workflows/`, `Dockerfile`,
`application*.properties`, and `*.env*`. A pattern starting with `**/` also
matches at the repository root. Replace the list with the comma-separated
`RELEASEFLOW_SENSITIVE_PATHS` (brace groups such as `{a,b}` are not
supported). An invalid pattern or an empty list stops the application from
starting, so the rule cannot be switched off by accident.

A Project can add its own patterns to this baseline, for example
`**/billing/**`, but can never remove any of it. Follow **Sensitive paths** on
a Project's card, or call `GET /api/projects/{projectId}/sensitive-paths`,
which returns the `baseline`, the Project's `additions`, the `effective` list,
and who last changed it. Every member can read them; only administrators
change them, one pattern per line on the page or with
`PUT /api/projects/{projectId}/sensitive-paths` and `{"additions": [...]}`.
Patterns are trimmed, and blank lines and repeats are dropped. A Project can
add at most 100 patterns of at most 256 characters; a pattern that does not
compile, or too many or too long ones, return `400 invalid_sensitive_paths`.
New patterns apply to changes classified afterwards, including an AI retry;
changes already classified keep their triggers. See
[ADR-0014](docs/adr/0014-project-sensitive-paths.md).

Open `/changes` to browse a Project's inbox, or call
`GET /api/projects/{projectId}/changes`. Both accept `category` (a category
code such as `feature` or `security`, in any case), `status`
(`needs-review`, `classified` for changes settled by rules without a person,
`reviewed`), and `context=insufficient` (see
[Review signals](#review-signals)). A value that is not a code or status returns
`400 invalid_change_filter`, and another Organization's Project returns
`404 project_not_found`. Each change returns its `category` code with its
`categoryName` and `categoryGroup`, as recorded when it was classified.

## AI classification and summaries

AI is optional. To enable it, choose one provider and give it a key and a
model before starting the application:

```bash
export RELEASEFLOW_AI_PROVIDER=anthropic          # or openai, deepseek
export RELEASEFLOW_ANTHROPIC_API_KEY='replace-with-an-api-key'
export RELEASEFLOW_ANTHROPIC_MODEL='replace-with-a-model-id'   # for example claude-opus-5
```

| Provider | Key and model variables | Base URL variable (default) |
| --- | --- | --- |
| `openai` | `RELEASEFLOW_OPENAI_API_KEY`, `RELEASEFLOW_OPENAI_MODEL` | `RELEASEFLOW_OPENAI_BASE_URL` (`https://api.openai.com/v1`) |
| `anthropic` | `RELEASEFLOW_ANTHROPIC_API_KEY`, `RELEASEFLOW_ANTHROPIC_MODEL` | `RELEASEFLOW_ANTHROPIC_BASE_URL` (`https://api.anthropic.com`) |
| `deepseek` | `RELEASEFLOW_DEEPSEEK_API_KEY`, `RELEASEFLOW_DEEPSEEK_MODEL` | `RELEASEFLOW_DEEPSEEK_BASE_URL` (`https://api.deepseek.com`) |

There is no default model; pick one that supports structured JSON output.
Startup fails when the selected provider lacks its key or model, when the
provider name is unknown, or when a provider's key or model is set but
`RELEASEFLOW_AI_PROVIDER` is empty. `RELEASEFLOW_AI_TIMEOUT` (default `PT60S`)
bounds each request. With no provider, changes are classified by the rules
alone.

With a provider, the change worker asks the AI **once** for every new change,
after its changed files are known. It sends the pull request title, labels,
target branch, up to 4000 characters of the description, the Organization's
output language, the Organization's active [categories](#categories), the
category the rules chose, if any, and the code and communication intent of
each [audience](#audiences). It does not send the
author, and OpenAI requests set `store: false`. The answer is one category code from the
catalog (or a proposal for a new category when none fits), a breaking flag, whether a person should review it, a **neutral summary** (what
changed, why, technical detail, migration step), and a **narrative** for each
audience, all written in the output language. A missing narrative is simply
left out. The Change Inbox shows the summary and narratives under each change,
and the changes API returns them as `neutralSummary` and `audienceNarratives`
with `contentLanguage` and `aiProvider`. A summary written by a person on a
release page is never replaced by a later AI answer.

The rules always win:

- a category the rules chose is kept;
- the AI may mark a change breaking but never clears the flag;
- breaking, Unknown, and triggered changes, and changes the AI asks to have
  reviewed, stay in review.

Otherwise the AI's answer settles the change, and an Unknown change can
receive its category from the AI.

If the AI call fails or returns something unusable, the change keeps the rule
result, records `aiStatus: FAILED` with a safe message, and gets an
**AI classification failed** review trigger. Nothing is retried automatically,
and a worker that stops mid-call never asks again. A person can press **Retry
with AI** in the Change Inbox, or call
`POST /api/projects/{projectId}/changes/{changeId}/ai-classification` with the
session and a CSRF token. The same applies to Unknown changes recorded before
automatic AI. The failure trigger stays, so a person still reviews the change.

| Outcome | REST response |
| --- | --- |
| AI answer stored | `200` with the updated change |
| Change not in this Organization's Project | `404 change_not_found` |
| Change still being processed | `409 change_processing` |
| Change already classified by AI, or reviewed | `409 change_not_eligible_for_ai` |
| The provider failed; the failure is stored and shown | `502 ai_classification_failed` |
| AI not configured | `503 ai_classification_unavailable` |

## Workspace overview

Signing in opens an overview of one Project: how many changes it has, how many
need review, how many are breaking, and how many releases it has — each a link to
the page that can act on it. Beside them is the **one** next step that is
unfinished, in a fixed order: create a project, connect a source, wait for the
first change, collect changes into a release, finish a review, publish an approved
release, continue a draft, or nothing waiting. A workspace with an open draft and
no changes at all is still waiting for its first change, because there is nothing
to put in the draft.

The header switches between Projects. ReleaseFlow remembers the last one in the
`releaseflow_project` cookie, so a page reached without `?project=` opens on it
rather than on whichever Project happens to be first. A URL that names a Project
always wins, the cookie is `HttpOnly`, and it carries no authority: the Project is
still looked up against your Organization, so a cookie naming somebody else's is
ignored.

A mistyped address answers a browser with a page rather than raw JSON, and one
your role does not reach says so without showing any of it. A REST client, and
anything under `/api`, keeps the `application/problem+json` it had.

## Interface language

Each person reads ReleaseFlow in English or Vietnamese. The language is picked
per request, in this order:

1. the `releaseflow_lang` cookie;
2. the language saved on the account (`app_users.ui_locale`);
3. `Accept-Language`, narrowed from a region to a language, so `vi-VN` asks
   for `vi`;
4. the first entry of `RELEASEFLOW_UI_LANGUAGES` (default `en,vi`).

A step that names a language this deployment does not ship falls through to the
next one. The picker sits in the user menu; choosing a language saves it on the
account and writes the cookie, which outranks the account, so the change shows at
once. The cookie is `HttpOnly`, lasts a year, and is `Secure` when the request
was.

```text
GET /api/me/ui-locale                  (any member)
PUT /api/me/ui-locale {"uiLocale"}     (the same member)
```

Both return `{uiLocale, supported}`. `uiLocale` is null when the browser decides,
which is also what a new account does and what sending `null` returns to. A
language this deployment does not ship returns `400 ui_locale_invalid`.

Public changelog pages follow the same order, minus the account step: reading a
changelog still creates no session. They declare `Vary: Accept-Language, Cookie`,
because the note inside them is the same for everybody and the chrome is not. The
RSS feed is one shared document and stays English.

Page copy, form messages, and the `title` and `detail` of every
`application/problem+json` response are written in this language. **The `code` of
an error never changes**, so a caller matching on it is unaffected. Recorded
evidence is not interface text and stays as it was recorded: a stored AI failure,
a classification reason, and a provider's own name read the same in every
language.

This is not the language of anything ReleaseFlow writes. Release notes and AI
summaries follow the Organization's output language and its release note
languages, below, whichever language the person approving them was reading in.

## Output language

Each Organization writes its AI summaries in one output language, a language
tag such as `en`, `vi`, or `pt-BR`. It is chosen at registration
(`outputLanguage`, English by default) and shown on the Projects page, where
administrators can change it. Changes affect only changes classified
afterwards.

```text
GET /api/organization/output-language                     (any member)
PUT /api/organization/output-language {"outputLanguage"}  (administrator)
```

Both return `{outputLanguage, displayName, supported}`. Tags are canonicalized
(`vi_VN` becomes `vi-VN`); any tag whose language is an ISO 639 code is
accepted, and an invalid one returns `400 output_language_invalid`.
`RELEASEFLOW_OUTPUT_LANGUAGES` (default `en,vi`) lists the suggestions offered
in the forms.

## Human review

Every change in the Change Inbox has a review form prefilled with its current
category and breaking flag. Changes that need review show the form directly;
other changes show it under **Edit classification**. The reviewer keeps or
changes the values and presses **Confirm review**. ReleaseFlow stores exactly
the submitted values, clears the need for review, and records the reviewer and
time. A reviewer chooses an active category of the catalog, since a reviewed
change cannot stay Unknown, and may clear a breaking flag set by the rules or
AI.

Confirming without changes keeps the original classification source (rules or
AI). Changing the category or breaking flag makes the reviewer the source,
shown as "Corrected by". A change can be reviewed again; the latest review is
kept.

REST clients call `POST /api/projects/{projectId}/changes/{changeId}/review`
with the session, a CSRF token, and `{"category": "fix", "breaking": false}`.
The category is a code of the catalog in any case. It returns the updated
change, `400 invalid_change_review` for `unknown`, an archived category, or a
code that is not in the catalog, `400 validation_failed` when a field is missing, and
`404 change_not_found` for another Organization's change.

## Review signals

Two signals point reviewers at changes that deserve a closer look. Both only add
a need for review; they never merge, remove, or settle a change. See
[ADR-0013](docs/adr/0013-context-sufficiency-and-duplicates.md).

**Context sufficiency.** With AI enabled, the AI also scores from 0 to 100 how
much evidence the pull request gives about what changed and why. Fixed caps can
only lower that score:

| Pull request | Score at most | Reason |
| --- | --- | --- |
| Empty description | 30 | `DESCRIPTION_MISSING` |
| Title shorter than 12 characters | 50 | `TITLE_TOO_SHORT` |
| Title of just fix, update, change, cleanup, misc, or wip, with a description under 160 characters | 40 | `GENERIC_TITLE` |

A score below `RELEASEFLOW_CONTEXT_THRESHOLD` (default 60, 0–100) needs review
with a **Not enough context** trigger. The Change Inbox shows the score, and
`context=insufficient` filters to these changes; the changes API returns
`context` (`score`, `status`, `reasons`). Without an AI answer, context is not
assessed.

**Possible duplicates.** When a change finishes processing, it is compared with
the Project's other changes from the last 180 days (at most 500). The
comparison uses title, content (description and what changed), and changed
files. A pair at least `RELEASEFLOW_DUPLICATE_THRESHOLD` similar (default
0.82) is recorded with its evidence, up to five per change. The new change
needs review with a **Possible duplicate** trigger. Both cards show the pair,
and any member can **Confirm duplicate** or mark it **Not a duplicate**, once.

```text
GET  /api/projects/{projectId}/duplicate-candidates?status=OPEN
POST /api/projects/{projectId}/duplicate-candidates/{candidateId}/decision   {"decision": "CONFIRMED" | "DISMISSED"}
```

A decision that is missing or `OPEN` returns `400 validation_failed`, a second
decision `409 duplicate_candidate_decided`, and an unknown or foreign candidate
`404 duplicate_candidate_not_found`.

## Categories

Every Organization has its own category catalog. It starts with the six
categories that used to be fixed: Feature, Fix, Performance, Documentation,
Maintenance, and Unknown. Administrators manage it on the **Categories** page
or through `/api/categories`; every member can read it.

A category has a code (letters, digits, and underscores, starting with a
letter; upper-cased and fixed once created), a display name, and one of six
groups: Feature, Fix, Performance, Documentation, Maintenance, or Other.
Release notes are sectioned by group, so a new `SECURITY` category in the Fix
group appears under **Bug Fixes**. Breaking stays a separate flag.

- **Rules.** Each fixed rule names a group and a preferred code, for example
  `fix` names Fix and `FIX`. The preferred category is used if it is active;
  otherwise the first active category of the group by code; if the group has
  none, the rule locks nothing and the change is left to the AI or a person.
- **Archiving** stops offering a category to the rules, the AI, and reviewers.
  Changes that already carry it keep it, with the name and group they were
  given, and it can be restored. Unknown is a system category: it can be
  renamed but not regrouped or archived.
- **AI proposals.** When no category fits, the AI returns Unknown with a
  proposed category. The change needs review with an **AI proposed a new
  category** trigger, and the proposal waits on the Categories page and on the
  change's card in the Change Inbox. An administrator can:
  - **Add to catalog**: the category is created, or restored if it was
    archived;
  - **Map** the proposal to an existing active category;
  - **Reject** it.

  After Add to catalog or Map, the change takes that category, shown as
  **Suggested category**, and still needs a person's review. A proposal never
  becomes a category by itself, and each is decided once.

See [ADR-0012](docs/adr/0012-category-catalog.md).

```text
GET    /api/categories                                   (every member)
POST   /api/categories                                   {"code", "displayName", "group"}
PUT    /api/categories/{categoryId}                      {"displayName", "group"}
DELETE /api/categories/{categoryId}                      (archives it)
POST   /api/categories/{categoryId}/unarchive
GET    /api/category-suggestions?status=PENDING_REVIEW
POST   /api/category-suggestions/{suggestionId}/decision {"decision": "APPROVED" | "MAPPED" | "REJECTED", "categoryId"}
```

| Situation | Response |
| --- | --- |
| A member changing the catalog or deciding a proposal | `403` |
| A missing field, a code that is not a valid code, or MAPPED without `categoryId` | `400 validation_failed` |
| The code is already in the catalog | `409 category_code_taken` |
| Regrouping or archiving Unknown | `409 category_system` |
| Deciding a proposal again | `409 category_suggestion_decided` |
| An unknown or foreign category, or mapping to an archived one | `404 category_not_found` |
| An unknown or foreign proposal | `404 category_suggestion_not_found` |

## Audiences

Every approved release gets one release note per **audience**. Administrators
manage audiences on the **Audiences** page or through `/api/audiences`. Each
Organization starts with three presets, named in its output language
(English or Vietnamese):

| Code | Writes for | Template |
| --- | --- | --- |
| `operator` | operational risk, rollback, what to watch | what changed with the pull request link and narrative, then why, detail, and migration |
| `contributor` | implementation detail and code changes | as operator, with implementation instead of detail |
| `end_user` | plain language, what people notice | what changed and the narrative only |

An audience has a **code** (lowercase letters, digits, and underscores,
starting with a letter; fixed once created), a **display name**, a
**communication intent** that the AI follows when it writes the audience's
narrative, and a **template**. Templates are
[Mustache](https://mustache.github.io/mustache.5.html) and render one change as
Markdown with the variables `whatChanged`, `whyChanged`, `technicalDetail`,
`migrationStep`, `narrative`, `pullRequestNumber`, `pullRequestUrl`, and
`linkedIssues` — a list whose items have `key`, `title`, `type`, `status`, and
`url`, escaped like any other tracker text. A section such as
`{{#narrative}} — {{.}}{{/narrative}}` disappears when the value is empty. **Preview** renders a sample change. A template is checked when
it is saved: it must compile, use only these variables, and use `{{narrative}}`
rather than naming another audience.

An Organization keeps between 1 and 20 audiences. An audience that a release
note already uses cannot be deleted, and a preset can be reset to its shipped
version in the current output language. See
[ADR-0011](docs/adr/0011-audience-release-notes.md).

```text
GET    /api/audiences
POST   /api/audiences                          {"code", "displayName", "communicationIntent", "templateBody"}
GET    /api/audiences/{audienceId}
PUT    /api/audiences/{audienceId}             {"displayName", "communicationIntent", "templateBody"}
PUT    /api/audiences/{audienceId}/templates/{language}   {"templateBody"}
DELETE /api/audiences/{audienceId}
POST   /api/audiences/{audienceId}/reset-to-preset
POST   /api/audiences/preview                  {"templateBody"} -> {"markdown", "html"}
```

| Situation | Response |
| --- | --- |
| A member, not an administrator | `403` |
| A missing field, or a code that is not `[a-z][a-z0-9_]*` | `400 validation_failed` |
| A template that does not compile or uses an unknown variable | `400 template_invalid` |
| A template naming `narratives.<code>` | `400 template_narratives_path` |
| The code is already used | `409 audience_code_taken` |
| The Organization already has 20 audiences | `409 audience_limit` |
| Deleting the last audience | `409 audience_last` |
| Deleting an audience that has release notes | `409 audience_in_use` |
| Resetting an audience your team created | `409 audience_not_preset` |
| A language template for a language that is not a release note language | `400 template_language_not_targeted` |
| An unknown or foreign audience | `404 audience_not_found` |

### Release note languages

By default every note is written in the Organization's output language. An
administrator can choose up to five **release note languages** at the top of
the Audiences page, or with `PUT /api/organization/release-languages` and
`{"targetLanguages": ["en", "vi"]}`; every member can read them with `GET`.
Tags are canonicalized and repeats dropped; an empty list, more than five, or
an invalid tag returns `400 invalid_release_languages`. Approving a release
then writes one note per audience and language, so three audiences in English
and Vietnamese make six notes.

Each audience has its own template for every release note language that the
output language does not cover, created when the language or the audience is
added: a shipped audience starts from its shipped template in that language,
with translated labels, and any other audience from a copy of its main
template. Edit them under **Language templates** on the audience's page or
with `PUT /api/audiences/{audienceId}/templates/{language}`. Resetting a shipped
audience resets these templates too.

A change's summary and narratives are written in the language the AI (or a
person) used. For a note in another language they are translated with
[DeepL](https://developers.deepl.com/docs) after approval, never inside it:

```sh
export RELEASEFLOW_TRANSLATION_PROVIDER=deepl   # the default is disabled
export RELEASEFLOW_DEEPL_API_KEY=...            # a key ending in :fx uses the free API
# RELEASEFLOW_DEEPL_BASE_URL overrides the host chosen from the key.
```

Translations are queued per change and language, and a background worker sends
them to DeepL in batches. A translated text is cached for the Organization, so
the same text is never sent twice, and an unchanged summary is never translated
again, even when a release returns to draft and is approved again. A rate limit,
a server error, or a network failure is retried up to five times with growing
delays; any other failure is final. A pull request title used in place of a
missing summary is not translated.

Until its translations are in, a note shows the untranslated text and is
**Translating**; the release page updates by itself when they arrive. A note
whose translation failed, or that needs translation while the provider is
disabled, is **Not translated**. **Retry translations** starts the failed ones
over, and editing such a note by hand makes it ready. A release is published only
when every note is ready: otherwise publishing returns
`409 translations_not_ready`, and the database refuses it as well. Editing a
summary after approval translates the new text. See
[ADR-0015](docs/adr/0015-multilingual-release-notes.md).

```text
GET  /api/organization/release-languages
PUT  /api/organization/release-languages                               {"targetLanguages"}
POST /api/projects/{projectId}/releases/{releaseId}/translations/retry
```

## Releases

Open **Releases** to prepare a Project's releases. The page creates a release
with a version (up to 50 characters), an optional summary, and an optional
planned release time in UTC, and lists the Project's releases with status
filters and counts. A Project can prepare several releases at once, and each
change belongs to at most one release.

A release moves through four steps, shown at the top of its page:

1. **Draft.** Add changes that have finished processing, by hand or with
   **Add all available**. A change that still needs review can be added; the
   release's review settles it. Edit the version and summary, remove changes,
   then **Request review**, which needs at least one change.
2. **In review.** The change list is fixed except for rejections. On the
   **Review** tab, each change gets a decision: **Approve as shown** confirms
   its category and breaking flag, **Edit classification** corrects them, and
   **Reject** removes the change from the release so it becomes available for
   another one. Approving and editing record the reviewer on the change itself,
   exactly like a review in the Change Inbox, with an optional note. An Unknown
   change must be edited. **Approve release** becomes available once every
   change has a decision.
3. **Approved.** The approver and time are recorded, and a release note is
   written for every audience in every release note language from its
   template (see [Release note languages](#release-note-languages)). Check each
   note's tab,
   edit a note's Markdown if needed (the note becomes **Manual** and no longer
   follows its template), then **Publish release**.
4. **Published.** See [Publishing](#publishing).

Until it is published, a release can be scheduled or unscheduled, returned to
draft (from review or approval, clearing its decisions and approval but not
the reviews on its changes), or discarded (its changes become available
again). Returning to draft also deletes the release notes; approving again
writes new ones.

Before approval, the preview shows what each audience's note would say. A note
starts with the release title and summary and a **What's New** overview with
counts, warns about breaking changes, and lists breaking changes first, then
features, fixes, performance, documentation, and maintenance. Each change is
written by the audience's template from its neutral summary and the audience's
narrative; without a summary, the pull request title is used, with its
Conventional Commit prefix removed. Labels are English or Vietnamese, following
the output language.

During review and after approval, **Edit summary** on the Review tab lets a
person write or correct a change's summary and each audience's narrative. This
works whether or not the AI wrote one. The change records who wrote it, and the
notes of an approved release that still follow their template are rendered
again. Notes reflect the changes at approval: to pick up a classification
corrected later in the Change Inbox, return the release to draft and approve it
again. See [ADR-0010](docs/adr/0010-release-review-lifecycle.md) and
[ADR-0011](docs/adr/0011-audience-release-notes.md).

REST clients use the same rules:

```text
GET    /api/projects/{projectId}/releases
POST   /api/projects/{projectId}/releases                                {"version", "summary", "plannedReleaseAt"}
GET    /api/projects/{projectId}/releases/{releaseId}
PUT    /api/projects/{projectId}/releases/{releaseId}                    {"version", "summary"}
DELETE /api/projects/{projectId}/releases/{releaseId}
PUT    /api/projects/{projectId}/releases/{releaseId}/schedule           {"plannedReleaseAt"} (null clears it)
GET    /api/projects/{projectId}/releases/{releaseId}/available-changes
POST   /api/projects/{projectId}/releases/{releaseId}/changes            {"changeIds": [...]} or {"allAvailable": true}
DELETE /api/projects/{projectId}/releases/{releaseId}/changes/{changeId} (remove, or reject during review)
POST   /api/projects/{projectId}/releases/{releaseId}/request-review
PUT    /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision
                                                                         {"action": "APPROVE" | "EDIT", "category", "breaking", "note"}
POST   /api/projects/{projectId}/releases/{releaseId}/approve
POST   /api/projects/{projectId}/releases/{releaseId}/return-to-draft
POST   /api/projects/{projectId}/releases/{releaseId}/publish
POST   /api/projects/{projectId}/releases/{releaseId}/translations/retry
PUT    /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/summary
                                                                         {"whatChanged", "whyChanged", "technicalDetail", "migrationStep", "narratives": {"<code>": "..."}}
GET    /api/projects/{projectId}/releases/{releaseId}/note-previews
GET    /api/projects/{projectId}/releases/{releaseId}/notes
PUT    /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}     {"content"}
GET    /api/projects/{projectId}/releases/{releaseId}/notes/{noteId}/download
GET    /api/projects/{projectId}/release-assignments
```

`plannedReleaseAt` is an ISO-8601 instant such as `2026-10-01T09:00:00Z`; a
value without an offset is read as UTC. A release returns its `changes`, its
`decisions` (`changeId`, `action`, `reviewerName`, `note`, `decidedAt`),
`reviewedCount`, `plannedReleaseAt`, `approvedAt`, `approverName`, and its
`notes` (`id`, `audienceCode`, `audienceName`, `language`, `content`,
`autoRerender`, `lastEditorName`, `updatedAt`, and `translationStatus`:
`READY`, `PENDING`, or `FAILED`). The download is `text/markdown`, named
`<version>-<audience code>-<language>.md`.
`release-assignments` lists which release, by ID, version, and status, each
of the Project's changes belongs to. Errors:

| Situation | Response |
| --- | --- |
| The operation does not fit the release's status | `409 release_status_conflict` |
| Review requested or approval attempted with no changes | `409 release_empty` |
| Approval attempted before every change has a decision | `409 release_review_incomplete` |
| `APPROVE` sent with a classification other than the change's current one | `409 classification_changed` |
| `unknown` or an unsupported category in a decision | `400 invalid_change_review` |
| An unreadable or past planned release time | `400 invalid_release_schedule` |
| A change still processing or already in another release | `409 change_not_releasable` |
| The version is already used in this Project | `409 release_version_taken` |
| A summary or note edit outside the allowed status | `409 release_status_conflict` |
| An audience template that cannot render at approval | `409 release_note_render_failed` |
| Publishing an approved release that has no notes | `409 release_notes_missing` |
| Publishing while a note is still translating or could not be translated | `409 translations_not_ready` |
| An empty summary or note, or a field over its limit | `400 validation_failed` |
| An unknown or foreign change | `404 change_not_found` |
| An unknown or foreign release | `404 release_not_found` |
| An unknown note of this release | `404 release_note_not_found` |

## Publishing

On an approved release's page, open **Publish release**. Publishing freezes
every audience's note, together with who published the release and when. The
page then becomes read-only. It shows who approved and published the release,
and offers one tab per audience and language with the rendered note, its
Markdown, a **Copy** button, and a **Download** link for GitHub Releases or a
changelog. Downloaded files are named `<version>-<audience>-<language>.md`.

A published release cannot be edited, scheduled, reviewed, discarded,
unpublished, or changed in content, and neither can its notes. Its changes can
still be corrected in the Change Inbox, but the published notes keep what was
published. PostgreSQL triggers enforce this even for direct SQL. Releases
published before audiences existed keep their single note, which is shown as
before. See [ADR-0005](docs/adr/0005-immutable-release-note-snapshots.md) and
[ADR-0011](docs/adr/0011-audience-release-notes.md).

REST clients call `POST /api/projects/{projectId}/releases/{releaseId}/publish`.
It returns the release with `status`, `publishedAt`, `publisherName`, and
`notes`. For a release published before audiences existed, `markdown` and
`preview` hold its legacy note instead. Publishing a release that is not
approved returns `409 release_status_conflict`; any operation on a published
release returns `409 release_published`.

## Automation

A rule delivers a published release note where its readers already are. Open
**Automation** in the sidebar, which administrators alone can see. A rule names
what makes it run, the project it watches (or every project), and the actions it
carries out, in order. Each action names the audience and language whose note it
delivers.

Five things can make a rule run:

| Runs when | What it needs | What happens |
| --- | --- | --- |
| Release published | Nothing | Every matching rule fires once per release |
| Run by hand | A published release and a request ID | The same request ID is the same run, never a second delivery |
| On a schedule | One published release, a six-field cron expression, and an IANA time zone | The release is delivered again on that schedule. Firings missed while ReleaseFlow was down become one catch-up run |
| Before a planned release | A notice of 0 to 365 days | An approved release with a planned time is announced that long before it. Moving the release earns one more reminder |
| Called by another system | Nothing; ReleaseFlow mints the path and the secret | A signed call names a published release and starts a run |

A schedule and a reminder take only the actions that tell people something —
Slack, email, and Microsoft Teams: nobody is watching
when they go off. A schedule takes its project from the release it repeats.
**Preview next run** on the page, or `POST /api/automation/rules/cron-preview`,
says when an expression would next fire before any rule keeps it.

Write the rule first; it starts disabled. Enabling it is the moment ReleaseFlow
checks that the deliveries can be made: the project and every audience belong to
your Organization, the language is one your Organization writes notes in, a
GitHub Release action's project has exactly one GitHub source, the deployment can
carry the action out at all, and the configuration and secret are usable. A rule
that is refused says which of those failed.

Eight kinds of action exist:

| Action | What it needs | What it does |
| --- | --- | --- |
| GitHub Release | The project's own GitHub source and its access token | Publishes the note as the release of the version's tag, marking the body so a repeat knows its own work |
| Slack | An incoming webhook URL, which is the action's secret | Posts the note to the channel, up to 39,000 characters |
| Email | `RELEASEFLOW_AUTOMATION_EMAIL_FROM` and an SMTP server | Sends the note to between 1 and 100 addresses |
| Public changelog | Nothing | Publishes the note on your own changelog page and RSS feed |
| Notion | The parent page's id and an integration token | Files the note as a child page titled `Release <version>`, up to 450,000 bytes |
| Confluence | A Cloud site, the account's email, a numeric space id, an optional parent page, and an API token | Creates a page titled `Release <version>` in that space, up to 2,000,000 bytes |
| Microsoft Teams | The Workflows callback URL, which is the action's secret | Posts the note into the chat or channel the flow targets, up to 28 KiB of request |
| Zendesk | A subdomain, an OAuth client ID and secret, a numeric section, and an optional user segment | Publishes the note as a Help Center article in the action's language, up to 1,000,000 bytes |

A Slack webhook URL must name an origin the deployment allows —
`hooks.slack.com` or `hooks.slack-gov.com` unless `RELEASEFLOW_SLACK_ALLOWED_HOSTS`
says otherwise — and is checked again before every delivery. A Confluence site is
checked the same way, against the shape rather than a list: it must be exactly one
label beneath `atlassian.net` over HTTPS, with nothing after the host. A Notion
page id is the 32 hexadecimal characters Notion shows, with or without dashes. A
Microsoft Teams callback must be the HTTPS Workflows URL the flow displays — one
label beneath `environment.api.powerplatform.com`, the Workflows trigger path, and
its own `sig` signature — and a Zendesk subdomain is one label, not a URL, so the
only help centre an action can reach is its own. A
Confluence page carries an HTML comment naming the run that wrote it; nothing
reads it back, so a delivery Notion or Confluence never confirmed stays unknown
until a person says a second page is acceptable. A secret is
write-only: once stored, no response, page, or log ever repeats it, and leaving
the field empty while editing keeps the one already there.

### A rule another system calls

Saving a rule that runs when another system calls it gives it a path and a
256-bit secret, both shown once. **Rotate secret** replaces the secret and keeps
the path; the old secret stops working at once.

A call is a `POST` to that path with `{"releaseId": "…"}` and three headers:

| Header | What it holds |
| --- | --- |
| `X-ReleaseFlow-Timestamp` | Seconds since the epoch, within five minutes of now |
| `X-ReleaseFlow-Delivery` | A UUID, repeated on a retry so nothing is delivered twice |
| `X-ReleaseFlow-Signature-256` | `sha256=` and the hex HMAC-SHA256 below |

The signature covers the timestamp, the delivery, the uppercase method, the
request path, and the hex SHA-256 of the body, each on its own line:

```bash
BODY='{"releaseId":"<release>"}'
TS=$(date +%s)
DELIVERY=$(uuidgen)
PATH_='/webhooks/automation/<webhook>'
DIGEST=$(printf '%s' "$BODY" | openssl dgst -sha256 -hex | cut -d' ' -f2)
SIGNATURE=$(printf '%s\n%s\nPOST\n%s\n%s' "$TS" "$DELIVERY" "$PATH_" "$DIGEST" \
  | openssl dgst -sha256 -hmac "$SECRET" -hex | cut -d' ' -f2)
curl -X POST "http://localhost:8080$PATH_" \
  -H "X-ReleaseFlow-Timestamp: $TS" \
  -H "X-ReleaseFlow-Delivery: $DELIVERY" \
  -H "X-ReleaseFlow-Signature-256: sha256=$SIGNATURE" \
  -H 'Content-Type: application/json' -d "$BODY"
```

The answer is `202` with `{"runId", "status", "statusPath"}`. A signed `GET` of
that `statusPath` says how the run ended. The Organization is the rule's, never
the body's. A body over 64 KiB is `413`; an unknown path, a rule that is turned
off, a moment more than five minutes out, and a wrong signature are all `401`.

Publishing a release records that it happened and nothing more, so a rule nobody
can carry out never holds up a release. Within a second the worker creates one
run per matching rule and walks its actions in order. The run history shows each
delivery and how it ended. The first action that fails stops the ones behind it,
and **Run again** repeats only that one. An action whose outcome nobody could
confirm — a connection that dropped after the request went out, or a worker that
stopped for five minutes — is recorded as unknown and never sent again on its
own; repeating it asks you to confirm the delivery may happen twice. **Cancel**
lets the action already on its way record its result and stops the rest.

REST clients do the same thing:

```bash
curl -X POST http://localhost:8080/api/automation/rules \
  -H 'Content-Type: application/json' \
  -d '{"name":"Announce","triggerType":"RELEASE_PUBLISHED","projectId":"<project>",
       "actions":[{"actionType":"SLACK","audienceId":"<audience>","language":"en",
                   "secret":"https://hooks.slack.com/services/T0/B0/secret"}]}'
curl -X POST http://localhost:8080/api/automation/rules/<rule>/enable
curl -X POST http://localhost:8080/api/automation/rules/<rule>/execute \
  -H 'Content-Type: application/json' \
  -d '{"releaseId":"<release>","requestId":"<uuid you choose>"}'
curl 'http://localhost:8080/api/automation/runs?page=0&size=20'
curl -X POST http://localhost:8080/api/automation/runs/<run>/retry \
  -H 'Content-Type: application/json' -d '{"confirmUnknown":true}'
```

`POST /{id}/execute` answers `202` with the run. The request ID is yours to
choose and is the promise that a repeat is the same run rather than a second
delivery. `DELETE /api/automation/rules/{id}` archives a rule, which cancels the
runs it had not finished and frees its name. `GET /api/automation/runs` answers
`{"items", "page", "size", "total"}`, with `size` between 1 and 100.

A public changelog action's refusal is `public_changelog_conflict`, recorded on the
action rather than returned to anybody; see [Public changelog](#public-changelog).

Refusals carry a stable code: `automation_rule_name_taken`,
`automation_github_source_missing`, `automation_email_not_configured`,
`automation_run_not_retryable`, `automation_unknown_needs_confirmation`,
`automation_release_not_published`, `automation_scheduled_action_unsupported`,
and `automation_webhook_rule_required` are `409`; `automation_action_required`,
`automation_audience_not_found`, `automation_language_not_configured`,
`automation_slack_webhook_invalid`, `automation_notion_parent_required`,
`automation_notion_parent_invalid`, `automation_notion_token_required`,
`automation_confluence_site_required`, `automation_confluence_site_invalid`,
`automation_confluence_email_required`, `automation_confluence_email_invalid`,
`automation_confluence_space_required`, `automation_confluence_space_invalid`,
`automation_confluence_parent_invalid`, `automation_confluence_token_required`,
`automation_teams_webhook_required`, `automation_teams_webhook_invalid`,
`automation_zendesk_subdomain_required`, `automation_zendesk_subdomain_invalid`,
`automation_zendesk_client_id_required`, `automation_zendesk_client_secret_required`,
`automation_zendesk_section_required`, `automation_zendesk_section_invalid`,
`automation_zendesk_user_segment_invalid`,
`automation_cron_invalid`,
`automation_cron_time_zone_invalid`, `automation_cron_no_occurrence`,
`automation_cron_release_required`, `automation_reminder_days_invalid`, and
`invalid_automation_run_page` are `400`. Every automation path is administrator
only, and a rule from another Organization is `404`, never `403`. The signed
webhook paths are the exception: they take no session, and refuse with
`webhook_signature_invalid` (`401`), `webhook_payload_too_large` (`413`), or
`webhook_payload_malformed` (`400`). See
[ADR-0020](docs/adr/0020-automation-rules-and-runs.md) and
[ADR-0021](docs/adr/0021-scheduled-and-signed-automation-triggers.md).

## Public changelog

A rule with a **Public changelog** action publishes the note on pages ReleaseFlow
serves itself, for people who have no account:

| Address | What it is |
| --- | --- |
| `/changelog/{slug}` | The Organization's release notes, newest first, cached for five minutes |
| `/changelog/{slug}/releases/{entry}` | One note, permanent, cached for a day with an ETag |
| `/changelog/{slug}/rss.xml` | RSS 2.0 with the 50 newest notes |

The `{slug}` is your Organization's address: one DNS label, made from its name when you
register. The **Public changelog** card on the Projects page shows it and lets an
administrator change it — which moves the changelog, so links already shared stop
working. `GET`/`PUT /api/organization/slug` does the same thing, and answers
`400 organization_slug_invalid` or `409 organization_slug_taken`.

An entry is a snapshot: the Organization's name, the project, the version, the audience,
and the note as they read when it was published. Nothing edits or removes it afterwards,
not even direct SQL. Publishing the same note again is free — the action finds its own
entry and succeeds — while a rule that would publish *different* words for a release,
audience, and language that are already public fails as
`public_changelog_conflict` rather than rewriting what somebody may have read. A
public changelog action takes no secret and no configuration, and cannot be used by a
rule that fires on a schedule.

Set `RELEASEFLOW_PUBLIC_BASE_URL` to the address this deployment is reached at (default
`http://localhost:8080`); every public link — the canonical URL, the RSS items, the
address a run reports — is built from it and never from a request's `Host` header.

Setting `RELEASEFLOW_PUBLIC_CHANGELOG_BASE_DOMAIN` additionally serves each Organization
at `https://{slug}.{that domain}/`, with `/releases/{entry}` and `/rss.xml` beneath it.
Only GET is routed that way, only for exactly one label beneath the domain, and wildcard
DNS and TLS are yours to provision. Leave it empty and changelogs answer by path only.

## Verify

Docker must be available because persistence tests use PostgreSQL rather than
an in-memory substitute.

```bash
./mvnw test
./mvnw verify
```

The browser suite runs separately, because it drives the demo Compose stack:

```bash
npm ci
npm run e2e:install   # downloads Chromium, once
npm run e2e
```

It brings the stack up itself, so `.env` must be filled in first. It checks the
release pipeline, invitations, and every page, at 360 px in the light theme and
1440 px in the dark one, and **fails on any WCAG 2 A/AA, 2.1 A/AA or 2.2 AA
violation Axe can see**. Point it at an application already running with
`RELEASEFLOW_E2E_BASE_URL`.

While changing templates or styles, rebuild the stylesheet on every save in a
second terminal:

```bash
PATH="$PWD/node:$PATH" ./node/npm run watch
```

The application calls the GitHub API only to check an access token and to list
the changed files of a merged pull request; it does not import history or
validate repositories when they are connected. Nothing is published from a local
build: artifacts come from the `Release` workflow, and only from a tag.

## Continuous integration

GitHub Actions runs these checks on every push and pull request to `develop`
and `main`:

| Workflow | Checks |
| --- | --- |
| `CI` | Conventional pull request title, actionlint, `npm audit --audit-level=high`, `./mvnw clean verify` |
| `CodeQL` | Java and JavaScript analysis (also weekly) |
| `Dependency Review` | Fails pull requests that add high or critical vulnerabilities |
| `Secret Scan` | Gitleaks over the complete Git history (also weekly) |
| `Container` | Image build, Trivy high/critical scan, Compose smoke test, and the management port proven private |

Actions are pinned to commit SHAs and images to digests; Dependabot proposes
weekly updates to `develop`. The pinned tools in `scripts/ci/` verify their
checksums, so the scans can be reproduced locally, for example:

```bash
./scripts/ci/install-gitleaks.sh "$HOME/.local/bin"
gitleaks git . --redact
```

See [ADR-0007](docs/adr/0007-ci-and-container-supply-chain.md) and
[ADR-0026](docs/adr/0026-deployment-observability.md).

## Published artifacts

ReleaseFlow's own releases, as opposed to the ones it writes notes for.

A release is a tag. Pushing `v<version>` runs the `Release` workflow, which
refuses a tag that is not an ancestor of `main`, one whose name disagrees with
the POM, or any `-SNAPSHOT` — so a mistaken tag fails rather than ships. It then
runs the whole suite again, builds and scans the image, and publishes:

- `releaseflow-<version>.jar`, the executable JAR;
- `releaseflow-<version>-sbom.cdx.json`, a CycloneDX SBOM of the image, which
  lists the Java dependencies and the base image's packages together;
- `SHA256SUMS`, `LICENSE` and `NOTICE`;
- the image on GHCR, tagged `<version>`, `<major>.<minor>`, `<major>` and
  `latest`;
- a provenance attestation for the image and the JAR.

Check what you downloaded:

```bash
sha256sum --check SHA256SUMS
gh attestation verify releaseflow-<version>.jar \
    --repo hoangluongtran0309/ai-powered-release-notes-generator
```

Pin a deployment to the digest rather than to a moving tag:

```bash
docker pull ghcr.io/hoangluongtran0309/releaseflow@sha256:<digest>
```

## Contributing

Development uses `main` as the releasable branch, `develop` as the integration
branch, and short-lived branches for one reviewed change at a time. See the
[contributor guide](CONTRIBUTING.md) for branch and Conventional Commit rules.

## Security

ReleaseFlow holds webhook signing secrets, provider access tokens, and the
credentials its automation actions deliver with, and it answers unauthenticated
webhooks. If you find a weakness, please report it privately rather than in an
issue: see the [security policy](SECURITY.md).

## Licence

ReleaseFlow is one Maven module under the [Apache License 2.0](LICENSE)
([ADR-0029](docs/adr/0029-one-module-and-the-apache-licence.md)). You may use,
modify and redistribute it, including commercially, provided you keep the licence
and the [notice](NOTICE) and state what you changed.

There is no open-core or enterprise split. The earlier project this one was
rebuilt from sold its template, automation and distribution capabilities from a
separate proprietary module; here they sit beside every other capability, under
the same licence as the rest.

Copyright 2026 Luong Tran Chu Hoang.
