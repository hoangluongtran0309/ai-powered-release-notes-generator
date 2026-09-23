# ADR-0010: Release review lifecycle

- Status: Accepted
- Date: 2026-09-14

## Context

A release went straight from `DRAFT` to `PUBLISHED`, a Project had one draft
at a time, and only changes that no longer needed review could join it. Teams
review a release as a whole before they ship it: someone goes through every
change it contains, corrects what is wrong, drops what does not belong, and
approves the release. Later work needs an approved state too: notes per
audience are generated at approval, and reminders need a planned release time.
Review already exists per change in the Change Inbox (ADR-0004, ADR-0009), and
the two review paths must not disagree.

## Decision

- A release moves `DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED`. Request review
  needs at least one change. Approval needs a decision on every change and
  records the approver and time. Publication is allowed only from `APPROVED`.
- A release can return to draft from `IN_REVIEW` or `APPROVED`; its decisions
  and approval are deleted. It can be scheduled, unscheduled, or discarded at
  any point before publication.
- A draft chooses its changes. Any change that finished processing may join,
  even one that still needs review; the release's review settles it before
  approval. A `PROCESSING` change still cannot join.
- A decision is `APPROVE` or `EDIT`, sent with the category and breaking flag
  the reviewer saw and an optional note. `APPROVE` must match the change's
  current classification and cannot confirm an Unknown change. Both actions
  call `ChangeReviewService.review`, so the change records the reviewer as an
  Inbox review would. `release_change_reviews` keeps one decision per change
  of the release; a later decision replaces it. The Inbox review stays as a
  shortcut and does not count as a release decision.
- Rejecting a change during review removes it from the release, with its
  decision, and makes it available again. Nothing is recorded on the change.
- A Project may prepare several releases at once. Each change still belongs to
  at most one release.
- All operations are open to every member of the Organization, as before.
- PostgreSQL enforces the lifecycle alongside the service. Release changes are
  inserted only into drafts, never updated, and deleted only before approval.
  Decisions are written only during review and deleted only before approval.
  The approval columns are required on `APPROVED` and forbidden on `DRAFT` and
  `IN_REVIEW`.
- Times are stored as instants. REST accepts ISO-8601 with an offset; the page
  sends a local time that is read as UTC, matching how pages show times.

## Consequences

- An approved release never contains a change that still needs review, and its
  change list and decisions cannot change without returning to draft.
- `release` now calls into `change` to record reviews, and `change` still does
  not depend on `release`.
- Releases published before this decision have no approval record; the
  approval constraint allows that for `PUBLISHED` rows, which cannot change.
- [ADR-0005](0005-immutable-release-note-snapshots.md) still holds: publication
  writes the immutable snapshot, now from an approved release.
- The note is still rendered at publication. Rendering notes at approval, and
  reminders based on the planned time, are later decisions.
- Classifications can still be corrected in the Change Inbox after approval,
  and the note published afterwards reflects the correction.
