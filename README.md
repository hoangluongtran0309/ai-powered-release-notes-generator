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
- PostgreSQL persistence managed by Flyway migrations `V1` through `V18`;
- tenant-scoped Project creation and listing through REST and Thymeleaf;
- one create-only GitHub repository integration per Project;
- a unique webhook identity and 256-bit signing secret for each integration;
- AES-256-GCM encryption at rest with one-time secret reveal;
- a signed GitHub webhook endpoint that records each merged pull request once
  as a normalized change, and shows the last verified delivery per repository;
- an optional, write-only GitHub access token per repository, verified with
  GitHub and stored encrypted;
- a durable processing queue that lists each merged pull request's changed
  files outside the webhook request, with bounded retries;
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
6. Connect a repository with `POST /api/projects/{projectId}/sources` and
   `{"type": "GITHUB", "owner", "repository"}`. Save the returned
   `webhookSecret` immediately; it is never returned again. A Project may have
   several repositories; `GET /api/projects/{projectId}/sources` lists them.
7. Optionally set a repository's access token with
   `PUT /api/projects/{projectId}/sources/{sourceId}/token` (see
   [GitHub access token](#github-access-token)).

Authenticated users can perform the same workflow at `/projects`. GitHub owner
and repository names are canonicalized to lowercase. A repository can be
connected to one Project per Organization, while different Organizations may
connect the same repository. Connecting a repository and setting its token are
for administrators; every member can list sources. An unknown source returns
`404 source_not_found`, and a repository already connected returns
`409 github_repository_already_connected`.

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

## GitHub access token

With an access token, ReleaseFlow lists the files each merged pull request
changed, so changes that touch sensitive files are always reviewed. Without
one, every new change needs review, because nothing can be ruled out.

Create a fine-grained personal access token limited to the repository, with
**Pull requests: Read-only** permission (a classic token needs `repo` for a
private repository). An administrator enters it under the repository in the
Project's card on the Projects page, or calls:

```text
PUT /api/projects/{projectId}/sources/{sourceId}/token {"token"}   (administrator; 204)
```

ReleaseFlow first asks GitHub for the repository's pull requests with the
token, then stores it encrypted with AES-256-GCM. The token is never returned;
the source only reports `accessTokenConfigured` and `accessTokenUpdatedAt`.
Sending a new token replaces the old one. Errors are `400 github_token_rejected`
when GitHub refuses the token, `503 github_unavailable` when GitHub cannot be
reached, `404 source_not_found`, and `403` for members.

## History import

Webhooks bring in pull requests merged after a repository is connected. To add
the ones merged before, an administrator opens the **History import** panel of
the Change Inbox and chooses **Import last 90 days** for a repository with an
access token, or calls the REST endpoints below. A background worker lists the
repository's closed pull requests, most recently updated first, 100 per page,
and records every one merged in the last 90 days through the same processing as
a webhook delivery: its changed files are listed and, when AI is configured, it
is classified once. A pull request already recorded, by webhook or by an
earlier import, is skipped, so imports and webhooks never duplicate each other.
Imported changes carry an **Imported** badge.

An import stops at the first pull request updated before its window, and after
500 new changes it stops as **Stopped at the limit** with its position saved;
**Resume** continues from there with a fresh count, and so does resuming a
failed import. When GitHub rate-limits the import, it waits as long as GitHub
asks (`Retry-After` or the rate-limit reset, at most an hour); other temporary
failures wait with growing delays. After five attempts, or at once when GitHub
refuses the token, the import fails, and a refused token marks the repository
as **Connection failing** until a new token is saved. Only one import of a
repository runs at a time.

```text
GET  /api/projects/{projectId}/imports                              (every member)
POST /api/projects/{projectId}/sources/{sourceId}/imports           (administrator; 202)
POST /api/projects/{projectId}/sources/{sourceId}/imports/resume    (administrator; 202)
```

Each import reports `status` (`PENDING`, `RUNNING`, `RETRY_SCHEDULED`,
`COMPLETED`, `PARTIAL`, or `FAILED`), `scannedCount`, `importedCount`, the
window, `lastSyncAt`, `lastErrorCode`, and `canResumeImport`. Errors are
`409 source_token_missing`, `409 source_sync_in_progress`,
`409 source_import_not_resumable`, and `404 source_not_found`. See
[ADR-0016](docs/adr/0016-integration-sources-and-history-import.md).

`RELEASEFLOW_GITHUB_API_BASE_URL` (default `https://api.github.com`) and
`RELEASEFLOW_GITHUB_TIMEOUT` (default `PT5S`) are optional.

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
`migrationStep`, `narrative`, `pullRequestNumber`, and `pullRequestUrl`. A
section such as `{{#narrative}} — {{.}}{{/narrative}}` disappears when the
value is empty. **Preview** renders a sample change. A template is checked when
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

The application calls the GitHub API only to check an access token and to list
the changed files of a merged pull request; it does not import history or
validate repositories when they are connected. No image or release artifact is
published.

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
