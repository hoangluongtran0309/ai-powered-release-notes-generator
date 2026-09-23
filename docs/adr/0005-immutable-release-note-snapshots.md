# ADR-0005: Immutable Release Note snapshots

- Status: Accepted
- Date: 2026-09-11

## Context

A Draft Release previews its release note from the current state of its
changes. Those changes stay editable: a reviewer can correct a category or a
breaking flag at any time. The product invariant requires that a published
Release Note is an immutable snapshot, so what readers saw at publication
must never change afterwards, whoever or whatever touches the database.

## Decision

- Publishing a draft writes one `release_notes` row in the same transaction
  that marks the release `PUBLISHED`, with the publisher and time. The row
  holds the version, the summary, the sections as JSONB, and the Markdown text.
- The sections are built from the changes as they are at that moment. The
  Markdown is rendered once from them and stored. Later changes to the
  formatting code therefore cannot alter published text.
- Reads of a published release come from the snapshot. Changes that were
  included stay reviewable in the Change Inbox, but correcting them never
  affects the published note, and `change` still does not depend on
  `release`.
- PostgreSQL triggers reject any UPDATE or DELETE of `release_notes`, any
  UPDATE or DELETE of a `PUBLISHED` release, and any INSERT, UPDATE, or
  DELETE of `release_changes` for a published release. The application also
  rejects these operations with `409 release_published`, so users see a clear
  error instead of a database failure.
- Versions are unique per Project ignoring case, so a published version cannot
  be reused by a later draft.
- An empty draft cannot be published.

## Consequences

- Published content survives later reclassification, code changes, and
  mistaken SQL alike.
- There is no unpublish, deletion, or correction of published text; a fix
  needs a new release.
- Tests reset release data with `TRUNCATE`, which does not fire row triggers.
  The application has no delete path for published data.
- Distribution to external channels and a public changelog are separate future
  decisions; the stored Markdown is the intended source for them.
