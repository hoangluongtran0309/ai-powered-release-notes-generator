# ADR-0008: Durable change processing and changed-file review triggers

- Status: Accepted
- Date: 2026-09-14

## Context

Classification looked only at the pull request title, labels, and description.
A pull request titled `chore: tidy` that edits a database migration, the
security configuration, or a CI workflow could be settled by the rules and
released without anyone looking at it. GitHub's `pull_request` webhook carries
the number of changed files, never their paths, so listing them needs a
separate, authenticated GitHub call. That call must not run inside the webhook
request, which GitHub abandons after ten seconds, or inside a database
transaction. It must also survive restarts and transient GitHub failures.

## Decision

- Each Project's GitHub integration may hold one access token. Only
  administrators set it, it is optional, and it is write-only: a new token
  replaces the old one, and reads only report whether one exists and when it
  was set. Before storing it, ReleaseFlow calls
  `GET /repos/{owner}/{repo}/pulls?state=closed&per_page=1`, which proves the
  permission the worker needs (reading pull requests), not merely that the
  repository is visible. The token is encrypted like the webhook secret, with
  a `github-access-token` purpose line added to the authenticated data.
- Webhook intake records the change as `PROCESSING` (Unknown and in review)
  and a `PENDING` row in `change_processing_jobs` in one transaction.
- Processing uses one table per kind of work, claimed with
  `FOR UPDATE SKIP LOCKED` by a scheduled worker. Claiming, the GitHub call,
  and recording the result are three separate steps, and the network call runs
  between the transactions. A claim is valid only while its time matches the
  job row, and jobs left `ENRICHING` for ten minutes are claimed again.
- Timeouts, network errors, rate limits, and 5xx responses are retried after 2
  and 4 seconds, up to three attempts. After that, and immediately when there
  is no token, when GitHub refuses the token, when the response is invalid, or
  when five pages of 100 files are all full, the change is still classified,
  with a `CHANGED_FILES_UNAVAILABLE` review trigger. A truncated list is never
  treated as complete.
- A changed path, or the previous path of a rename, that matches a configured
  JDK glob adds a `SENSITIVE_PATH` trigger. The deployment's pattern list
  (`RELEASEFLOW_SENSITIVE_PATHS`) has a default baseline. An invalid pattern or
  an empty list stops startup, so the rule cannot be disabled by accident.
- Review triggers only add a need for review. Only a recorded human review
  clears it, and the trigger is kept. A documentation-only file list selects
  the Documentation category, ahead of the title type.
- Only two trigger types exist. Keyword triggers are for sources without file
  lists and wait for those sources.

## Consequences

- Without a token, every new change needs review. This is deliberate: nothing
  can be ruled out.
- A change is briefly `PROCESSING` and cannot be reviewed, sent to AI, or
  released meanwhile. `changes_processing_unsettled` and
  `changes_triggers_require_review` enforce this in PostgreSQL as well.
- Changes recorded before Flyway `V10` keep their classification and are
  never processed.
- The worker runs in every application instance. `SKIP LOCKED` makes that safe
  without a leader, and later kinds of work reuse the same pattern in their own
  tables.
- Configured patterns are split on commas, so brace groups such as `{a,b}`
  cannot be used.
