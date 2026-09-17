# Implementation Status

## Implemented

- One Java 21, Spring Boot 4.1.1 Maven application.
- JSON status endpoint and server-rendered home page.
- Organization plus administrator AppUser registration through REST and
  Thymeleaf.
- PostgreSQL persistence and Flyway `V1`, with Hibernate schema validation.
- Flyway `V2` for tenant-scoped Projects and GitHub integrations.
- Canonical unique emails, BCrypt password hashes, and atomic registration.
- Session authentication, tenant-bearing principal, form login, POST logout,
  and CSRF protection shared by REST and UI.
- Authenticated session identity and a public CSRF-token endpoint.
- Problem Details responses with stable codes for implemented API failures.
- Project creation/listing through shared REST and Thymeleaf application
  behavior.
- One canonical GitHub repository per Project, unique inside an Organization.
- Per-integration webhook IDs, one-time 256-bit secret reveal, and AES-256-GCM
  encrypted secret storage with tenant-bound authenticated data.
- PostgreSQL Testcontainers coverage including migration, constraints,
  cross-tenant lookup, negative security paths, REST, and UI.
- Workspace UI built with Tailwind CSS 4, DaisyUI 5, the Thymeleaf Layout
  Dialect, and Alpine.js, with a light/dark theme and a Maven-managed Node.js
  toolchain.
- Signed GitHub webhook intake: a sessionless endpoint per integration,
  constant-time HMAC-SHA256 verification before payload parsing, tenant
  identity from the verified integration, and repository identity checks.
- Flyway `V3` for normalized merged-pull-request changes, idempotent per
  Project and pull request number, and the last accepted delivery time.
- `ping` acknowledgement, ignored unrelated events, and a Projects page setup
  step showing the last verified delivery.
- Deterministic classification of each recorded change from its title type,
  labels, and `BREAKING CHANGE` footer, with explainable reasons.
- Mandatory review for breaking and Unknown changes, also enforced by a
  database constraint; Flyway `V4` marks earlier changes Unknown.
- A per-Project Change Inbox through Thymeleaf and REST with category and
  review-status filters.
- Optional automatic AI classification (ADR-0009) with OpenAI, Anthropic, or
  DeepSeek behind one `AiChangeClassifier` interface: one request per change in
  the worker, a shared JSON contract with a neutral summary, rules and review
  triggers that always win, explicit FAILED states with a fallback trigger, no
  automatic retry, stale-claim completion without a second call, a manual retry
  for failures and older Unknown changes, and Flyway `V5` and `V11`.
- An Organization output language (BCP 47) chosen at registration, changed by
  administrators through Thymeleaf and REST, and used for AI summaries.
- Human review of any change through Thymeleaf and REST: the reviewer confirms
  or corrects the category and breaking flag, and the reviewer and time are
  recorded. Flyway `V6` adds the review columns, a same-tenant reviewer foreign
  key, and constraints so breaking, Unknown, and AI-suggested changes leave
  review only through a recorded review.
- Release management through Thymeleaf and REST: several releases per
  Project, version and summary, an optional planned release time, processed
  changes added by hand or all at once, removal, discard until publication,
  and a grouped release note preview. Flyway `V7` enforces one release per
  change and same-Project, same-tenant membership.
- A release review lifecycle (ADR-0010): request review, `APPROVE` or `EDIT`
  decisions on each change that also review the change, rejection that removes
  a change, approval with the approver recorded, return to draft, status
  filters, a progress stepper, and a release-assignment endpoint. Flyway `V12`
  adds the statuses, schedule and approval columns, `release_change_reviews`,
  and triggers that fix changes and decisions after approval.
- Immutable Release Note publication of an approved release: a snapshot of
  sections and Markdown, the publisher and time, a read-only page with
  copyable Markdown, unique versions per Project, and Flyway `V8` triggers that
  reject any change to published releases, their changes, and their notes.
- Integration sources and history import (ADR-0016), with Flyway `V18`:
  - `github_integrations` renamed in place to `integration_sources`, keeping
    webhook paths and ciphertexts, with several GitHub repositories per
    Project and changes identified by source;
  - a project-scoped sources API replacing `/github-integration`;
  - a durable, resumable import of the last 90 days of merged pull requests,
    paged with a cursor, limited to 500 new changes per run, honouring
    `Retry-After`, sharing the webhook intake and processing, and shown in the
    Change Inbox.
- Release notes in several languages (ADR-0015), with Flyway `V17`:
  - one to five release note languages per Organization, and one note per
    audience and language at approval;
  - per-language audience templates, made when a language or an audience is
    added;
  - DeepL translation of change summaries and narratives through a durable
    queue after commit, with a per-Organization cache, bounded retries, and a
    manual retry;
  - notes that are ready, translating, or not translated, a polling release
    page, and publication held until every note is ready, in the service and
    in the database.
- Sensitive paths per Project (ADR-0014), with Flyway `V16`: administrators add
  glob patterns to the deployment's baseline through Thymeleaf and REST,
  members read them, and the worker and AI retries check changes against the
  baseline plus the Project's additions.
- Context sufficiency and possible duplicates (ADR-0013), with Flyway `V15`:
  - an AI context score, lowered by fixed caps, with a review trigger below a
    configurable threshold;
  - an Inbox filter for insufficient context;
  - trigram and changed-file similarity against the Project's recent changes,
    with a trigger on the newer change;
  - evidence on both cards;
  - one-time decisions by any member through Thymeleaf and REST.
- A category catalog per Organization (ADR-0012), with Flyway `V14`:
  - administrator-managed categories with fixed codes and six groups, which
    can be archived and restored;
  - the former fixed values seeded at registration and by the migration;
  - rules that resolve a group's preferred category;
  - AI that chooses among active codes;
  - AI proposals of new categories held for an administrator's decision in the
    Categories page, the Change Inbox, and REST;
  - category snapshots on changes;
  - release notes sectioned by group.
- Audiences and a release note per audience (ADR-0011), with Flyway `V13`:
  - administrator-managed audiences with fixed codes, communication intents,
    and validated Mustache templates, through Thymeleaf and REST;
  - three presets seeded at registration and by `V13`, in English or
    Vietnamese, which can be reset;
  - one to twenty audiences per Organization, and audiences that notes use
    cannot be deleted;
  - a narrative per audience in the single AI request, with a response schema
    built for each request;
  - one note per audience written at approval as a digest with English or
    Vietnamese labels;
  - note editing that makes a note manual, and summary and narrative editing
    that records the writer and renders automatic notes again;
  - live previews, Copy and Download, and server-side CommonMark rendering;
  - triggers that allow note writes only while approved and freeze notes on
    publication;
  - a read-only legacy note for releases published before `V13`.

- Administrator and member roles with single-use, hashed, expiring member
  invitations through Thymeleaf and REST, a Members page, and Flyway `V9`.
- A two-stage, digest-pinned, non-root container image with a health check on
  `GET /api/status`, and a Docker Compose demo stack with PostgreSQL that
  requires its secrets to be supplied.
- GitHub Actions gates: Conventional PR titles, actionlint, npm audit, Maven
  verification, CodeQL, dependency review, a full-history Gitleaks scan, and
  a Trivy image scan with a Compose smoke test; weekly Dependabot updates.
- Optional, write-only GitHub access tokens set by administrators through
  Thymeleaf and REST, checked against the repository's pull requests before
  they are stored encrypted.
- A durable change processing queue: webhook intake records a `PROCESSING`
  change and a job; a scheduled worker claims jobs with `SKIP LOCKED`, lists
  changed files outside any transaction, retries transient GitHub failures,
  and recovers stale claims.
- Typed review triggers for sensitive paths and unavailable file lists, a
  configurable sensitive-path baseline, a documentation-only rule, and Flyway
  `V10` constraints that keep processing changes unsettled and triggered
  changes in review until a person reviews them.

- GitLab sources (ADR-0017), with Flyway `V19`:
  - a `source` package holding the provider-neutral vocabulary, and two ports —
    `ChangedFileCollector` and `SourceHistoryReader` — with one implementation
    per source type and a registry that fails at startup if a type has none;
  - GitLab projects named by their path, including subgroups, on an instance
    from the deployment's allowlist, refused unless it is a plain HTTP or HTTPS
    origin the allowlist names;
  - `POST /webhooks/gitlab/{webhookId}`, proven by Standard Webhooks headers
    within a five-minute skew or by `X-Gitlab-Token`, both in constant time, and
    a 1 MiB limit on any provider's delivery;
  - merged merge requests normalized into the same change, with the merge commit
    falling back to the squash and last commits and the merge time to the last
    update;
  - changed files from the merge request's diffs, ten pages at most, where a
    collapsed, too large, or truncated diff makes the whole list unavailable;
  - a 90-day history import that asks for the merge requests updated after the
    window's start, least recently updated first;
  - an access token confirmed against `GET /api/v4/projects/{key}` before it is
    stored, with GitLab's own error codes.

- Linear sources (ADR-0018), with Flyway `V20`:
  - a Linear team connected with the signing secret Linear minted and an API key,
    both write-only, confirmed with GraphQL `team(id)` before anything is stored,
    which is also how the workspace every delivery is checked against is learned;
  - `POST /webhooks/linear/{webhookId}`, proven by a delivery header, a stamp
    within 60 seconds, the workspace, and a hex HMAC of the raw body;
  - only an issue that really moves into a completed state recorded, by its UUID,
    with the number a person sees kept for release notes;
  - `ChangedFileStatus.NOT_SUPPORTED` and a `SENSITIVE_KEYWORD` trigger from the
    seven-word scan of a change's own title and description, so a source without
    files needs review only on a match;
  - the issue read back over GraphQL, so a change carries its current wording;
  - `changes.source_type`, tied to the source by a composite foreign key, which
    keeps a merge commit and a target branch on a code host's change and off an
    issue tracker's;
  - history import refused for a type that has none, in REST and in the UI.

- Jira sources and linked context (ADR-0019), with Flyway `V21`, which reaches
  parity milestone M2 — every planned source exists:
  - a Jira Cloud project connected with its `atlassian.net` site, key, account
    email, and API token, confirmed with Jira before anything is stored, and
    with no webhook or secret;
  - polling as `JIRA_POLL` source sync jobs that nobody requests, scheduled from
    each source's `next_poll_at`, reading issues that moved into Done through
    the JQL search with a ten-minute overlap, and keeping the cursor on failure;
  - `SourceSyncWorker` and an opaque `SyncCursor`, so imports and polls share
    one claim, retry, and recovery path;
  - linked context for GitHub and GitLab changes: up to 250 commit messages read
    only to find keys, up to ten Jira issues fetched, six recorded statuses, and
    the `LINKED_CONTEXT_UNAVAILABLE` trigger;
  - linked issues in the AI prompt, the Change Inbox, and audience templates;
  - a Jira card on the Projects page and a poll row in the Change Inbox.

## In progress

- Nothing. The Jira source slice is complete and awaiting review.

## Planned

Nothing is planned beyond the slices above. New work starts from the
deliberately deferred list below, one reviewed slice at a time.

## Deliberately deferred

- Role changes and member removal.
- Source providers other than GitHub, GitLab, Linear, and Jira Cloud (Jira
  Server or Data Center), Jira webhooks, and automatic AI retries.
- GitLab group-level webhooks, Linear projects and cycles, linked context from
  Linear or from more than one Jira source per Project, Jira issue types and
  statuses as classification signals, and queues or retries for work other than
  enrichment, imports, and polls.
- Validating a repository with GitHub when it is connected, disconnecting or
  replacing a source, rotating a Linear signing secret without reconnecting, and
  secret or token rotation reminders.
- Localization of the UI, translation providers other than DeepL, and
  asynchronous note generation.
- Automation, distribution integrations, and a public changelog.
- Client-rendered pages, JavaScript bundling and tests, browser end-to-end
  tests, production observability (Actuator, metrics, Prometheus, Grafana),
  image publication, release automation, and an open-core/enterprise module
  split.
- Multi-repository aggregation.
- Change Inbox pagination and search.
- Review history, comments, reviewer roles, and bulk review.
- Item reordering, and reminders or automation for planned release times.
- Unpublishing or correcting published release notes.
