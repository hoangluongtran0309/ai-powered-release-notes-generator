# ADR-0018: Linear sources

- Status: Accepted
- Date: 2026-09-16

## Context

ADR-0017 made GitLab the second source type behind two provider seams. Linear is
the third, and the first that is not a code host. That breaks three assumptions
the model had carried since V3:

- a change always has a merge commit and a target branch;
- a change without a file list is a change whose file list *failed to arrive*,
  which always forces review;
- every source type has a history ReleaseFlow can import.

Linear also inverts the credential flow: it mints the webhook signing secret
itself, and shows it once, so an administrator can only paste it in.

## Decision

- **One enrichment seam, not two half-fitting ones.** S13's
  `ChangedFileCollector` becomes `SourceEnricher`, whose `Enrichment` carries
  both the files a provider could list and the change with anything the provider
  restated. GitHub and GitLab list files and restate nothing; Linear lists no
  files and restates the issue. The worker classifies and prompts the AI from the
  restated change, so an issue edited between being completed and being read back
  is judged as it now reads.
- **A completed issue is a change.** Only `type: Issue` with `action: update`,
  an `updatedFrom` carrying a state, a new state of `completed`, and an old state
  that is not `completed` — a real transition into done, not an edit to something
  already done.
- **Linear's own number is the change's number.** `data.number` (the 123 in
  `ENG-123`) goes into `pull_request_number` and `data.id` into `external_id`.
  Release notes, previews, sorts, and the seeded audience templates therefore
  need no change at all: `#123` links to the issue and reads correctly.
- **A change records its source's type.** `changes.source_type` is tied to the
  source by the composite foreign key `(source_id, source_type)`, so it can never
  disagree, and `changes_commit_matches_type` then states the rule the model used
  to state with `NOT NULL`: a GitHub or GitLab change must have a merge commit
  and a target branch, and a Linear change must have neither.
- **Not supported is not unavailable.** `ChangedFileStatus.NOT_SUPPORTED` says
  the provider has no such notion, and the rules answer it with the keyword scan
  the old project used — `breaking change`, `migration`, `security`, `auth`,
  `credential`, `password`, `encryption`, matched as lowercase substrings of the
  title and description, one `SENSITIVE_KEYWORD` trigger per hit. A Linear change
  with none of them does **not** need review. `UNAVAILABLE` still forces it.
- **The secret is pasted, the token is required.** Linear generates the signing
  secret, so `POST .../sources` takes it write-only and never echoes it. The API
  key is required too, because confirming the team with GraphQL `team(id)` is
  also how ReleaseFlow learns the workspace it checks on every delivery. Both
  calls happen before anything is written, so `create` holds no transaction and
  each branch writes in one short transaction of its own.
- **A delivery proves itself with four things**: a `Linear-Delivery` header, a
  `createdAt` within 60 seconds of the clock (a numeric value above 1e10 is
  milliseconds), an `organizationId` equal to the stored workspace, and a hex
  HMAC-SHA256 of the raw body in `Linear-Signature`. Unlike the other verifiers
  this one reads the body, because Linear puts the timestamp and the workspace
  inside it; an unreadable body is one more way to fail, and every failure is the
  same `401`.
- **No history, and the product says so.** `SourceType.supportsHistoryImport()`
  is false for Linear, the history registry demands a reader only for types where
  it is true, `SourceImportService` answers `409 source_import_not_supported`, and
  the Change Inbox does not list the source in its History import panel.

## Consequences

- An issue tracker and a code host can feed one Project, and both reach the same
  intake, classification, AI, review, and release notes.
- A Linear change has no changed files to show and no merge commit or branch, so
  the Change Inbox omits those fields rather than showing them empty.
- The keyword scan is deliberately weaker than the path rules. It is what a
  source that cannot report files allows, and it is never used for one that can.
- Linear's webhook secret cannot be rotated from ReleaseFlow: rotating it in
  Linear means reconnecting the source. Disconnecting and replacing a source is
  still deferred.
- Jira, the last planned source, will also be `NOT_SUPPORTED` for files and will
  reuse the keyword scan; it needs polling rather than a webhook, which is S15.
