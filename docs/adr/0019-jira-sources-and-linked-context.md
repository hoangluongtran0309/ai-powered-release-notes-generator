# ADR-0019: Jira sources and linked context

- Status: Accepted
- Date: 2026-09-17

## Context

ADR-0018 made Linear the third source type and the first issue tracker. Jira is
the fourth and last planned source, and it differs from every earlier one in
three ways the model could not express:

- **Nobody triggers it.** The old project polled Jira every five minutes. Every
  job row ReleaseFlow wrote until now existed because something else happened —
  a delivery, an administrator's click, an approval — and
  `source_sync_jobs.requested_by` was `NOT NULL`.
- **It has no webhook.** `webhook_id`, `secret_nonce`, and `secret_ciphertext`
  were `NOT NULL`.
- **It explains other sources' changes.** A GitHub or GitLab change should show
  the Jira issues it mentions, which needs a *different* source's credentials,
  and most mentions are in commit messages, which neither client could list.

S13 and S14 both deferred commit-message collection to this slice, and the
eighth and last review trigger of the old project, `LINKED_CONTEXT_UNAVAILABLE`,
depends on it.

## Decision

- **A poll is a sync job nobody asked for.** It reuses `source_sync_jobs` as
  `job_type = 'JIRA_POLL'`, so claiming with `SKIP LOCKED`, `Retry-After`,
  backoff, stale recovery, and the one-active-job index all apply unchanged.
  `requested_by` and `requester_name` are nullable, and
  `source_sync_jobs_requester_matches_type` says only an import has them. A
  poll's `item_limit` is `Integer.MAX_VALUE`, so it never stops part-way.
- **The schedule lives on the source.** `poll_cursor_at` and `next_poll_at` are
  set when the source is connected (cursor = now, first poll one interval later,
  so nothing before the connection is read). Each tick of the renamed
  `SourceSyncWorker` first asks `JiraPollScheduler` for sources whose
  `next_poll_at` has passed and that have no active job, and inserts one poll for
  each with the window `[poll_cursor_at − 10 minutes, now]`. The overlap costs
  nothing, because intake keeps one change per issue. A completed poll moves the
  cursor to the window's end and books the next poll one interval later; a failed
  one keeps the cursor, marks the source `ERROR`, and comes back — after the
  job's own retries, or 15 minutes after a final failure.
- **The cursor is opaque to the worker.** `ImportCursor(page, offset)` becomes
  `SyncCursor(provider, offset)`, stored as `provider:offset` and split at the
  last colon. GitHub and GitLab put a page number in the provider half (blank is
  the first page); Jira puts its `nextPageToken`. `provider_cursor` widens to
  `VARCHAR(500)`. A row written before V21 (`1:2`) still parses.
- **Only an Atlassian Cloud site.** `JiraSiteUrl` accepts HTTPS, no userinfo,
  port, query, or fragment, and a host that is `atlassian.net` or ends in
  `.atlassian.net`. It is checked when connecting and again before every call;
  redirects are never followed, and a redirect answer confirms nothing. The
  deployment-only `RELEASEFLOW_JIRA_API_BASE_URL` sends every call to one fixed
  address instead — for tests and local runs — while the site a request named is
  still checked and still builds every issue link.
- **Basic credentials, required.** Jira takes `email:token`, so a Jira source
  stores the account email in `credential_identity` and the API token encrypted
  as before. Both are required and checked with `GET /rest/api/3/project/{key}`
  before anything is written.
- **An issue that is done is a change.** The JQL is
  `project = "KEY" AND statusCategory = Done AND updated >= "yyyy-MM-dd HH:mm"
  ORDER BY updated ASC`, 100 at a time. The window is judged on `resolutiondate`,
  falling back to `updated`, so an old issue that was merely edited is skipped.
  The number in the key is the change's number, Jira's internal ID its
  `external_id`, the title is `KEY-123: summary`, the author the reporter's
  display name, and the URL `{site}/browse/KEY-123`, built by ReleaseFlow. The
  description is Atlassian Document Format, flattened to at most 8000
  characters. A poll's changes are recorded with origin `IMPORT`, the only
  non-delivery origin there is.
- **Linked context is a second enrichment step.** `SourceEnricher` stays as it
  was for the three existing providers. After it, `LinkedContextEnricher` finds
  the Project's Jira source by type (the oldest, if there are several), reads
  the change's commit messages through a new
  `SourceEnricher.commitMessages(...)` (GitHub and GitLab only, at most 250, over
  three pages), and extracts the Jira project's keys from the title,
  description, target branch, and commits with the old project's pattern
  `(?<![A-Z0-9_])(KEY-[1-9][0-9]*)(?![A-Z0-9_-])`, case-insensitive, at most ten.
  Each key is fetched with `GET /rest/api/3/issue/{key}`.
- **Six statuses, two of which ask for a person.**

  | Status | When |
  | --- | --- |
  | `NOT_SUPPORTED` | The change's own source is Linear or Jira |
  | `NOT_CONFIGURED` | The Project has no Jira source with a token, or the change's own source has no token |
  | `NOT_FOUND` | Commits were read and no key was found |
  | `PARTIAL` | Commits could not be read, so a key may have been missed |
  | `UNAVAILABLE` | A key was found and Jira would not show that issue |
  | `COLLECTED` | Commits were read and every key resolved |

  `PARTIAL` and `UNAVAILABLE` are retried on the same budget as the source's own
  enrichment, so the change stays visibly Processing, and once attempts run out
  they add `LINKED_CONTEXT_UNAVAILABLE`. The trigger is raised by
  `ChangeClassifier`, beside `CHANGED_FILES_UNAVAILABLE`, rather than in
  `ChangeAiMerge` as first planned, because the classifier already turns an
  external outcome into a trigger and this covers the AI-disabled path too.
- **Issues are evidence, commit messages are not even that.** A change stores
  its issues in `linked_issues` and its status in `linked_context_status`; the
  AI prompt gets a top-level `linked_issues` array marked untrusted, and its
  response schema is unchanged. Commit messages are used to find keys and are
  never stored or sent to the AI.
- **Release notes may list issues.** Audience templates gain an additive
  `linkedIssues` section with `key`, `title`, `type`, `status`, and `url`. The
  description is left out on purpose, and every tracker value but the URL is
  escaped. The seeded templates do not use it, so no template migration is
  needed.

## Consequences

- ReleaseFlow now does work on its own schedule. It is still a job row, visible
  and retried like any other, and the Change Inbox shows each Jira source's last
  and next read instead of an import button.
- Every planned source exists, and parity milestone M2 is reached.
- A Jira change's title starts with its key, so the Conventional Commit title
  rule never matches one; without AI, a Jira change is Unknown and needs review.
  The old project behaved the same way.
- The key pattern rejects a key followed by a hyphen, as the old project's did,
  so a branch named `feature/APP-3-export` names no issue. A commit or title that
  mentions `APP-3` still does.
- A Project with several Jira sources is enriched from the oldest one only.
- Only Jira Cloud is supported. Jira Server or Data Center, and Jira webhooks,
  are out of scope.
- A poll that fails for good waits 15 minutes before the next one, and the
  source shows `ERROR` until one succeeds.
