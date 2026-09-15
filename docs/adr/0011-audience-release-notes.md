# ADR-0011: Audiences and a release note per audience

- Status: Accepted
- Date: 2026-09-15

## Context

A release had one note, grouped by category and rendered at publication
(ADR-0005). Different readers need different notes: operators care about risk
and rollback, contributors about what they must change in their code, end
users only about what they will notice. The AI already writes one neutral
summary per change (ADR-0009). Asking it again for every reader would multiply
cost and let the facts drift between notes. ADR-0010 added an approved state so
that notes could be prepared and checked before publication.

## Decision

- An Organization has **audiences**. Each has a code, a display name, a
  communication intent, and a Mustache template.
  - The code is `[a-z][a-z0-9_]*`, at most 64 characters, unique within the
    Organization, and fixed after creation.
  - Registration seeds three presets, `operator`, `contributor`, and
    `end_user`, named in the output language (English or Vietnamese). V13
    seeds the same presets for existing Organizations.
  - Administrators manage audiences through `/audiences` and `/api/audiences`.
    An Organization keeps between one and twenty audiences, and an audience
    that any note uses cannot be deleted.
  - A preset can be reset to its shipped version in the current output
    language.
- The single AI request per change also returns a **narrative** for every
  audience, following the audience's intent.
  - The response schema lists the audience codes, so it is built for each
    request.
  - Unknown keys are dropped. A missing or empty narrative is left out and does
    not force review; its template section simply disappears.
  - Narratives are stored on the change in `audience_narratives`, keyed by
    code. An audience added later has no narrative for earlier changes.
- A template renders one change as Markdown. It can use `whatChanged`,
  `whyChanged`, `technicalDetail`, `migrationStep`, `narrative`,
  `pullRequestNumber`, and `pullRequestUrl`.
  - JMustache runs in standards mode with strict sections, treats empty
    strings as false, and escapes nothing.
  - A template is validated on save: it must not be blank, must not name
    `narratives.<code>`, and must compile and render sample values.
  - Without a summary, what changed is the Markdown-escaped pull request title.
- A note is a digest:
  - a title and the release summary;
  - "What's New", with counts per section and a warning when there are breaking
    changes;
  - sections in a fixed order: breaking, features, fixes, performance,
    documentation, maintenance, other. Empty sections are left out.

  Item headings are demoted two levels. Labels come from resource bundles in
  English or Vietnamese, falling back to English. Rendering never calls AI.
- **Approval writes one note per audience**, in the approval transaction.
  - Each note keeps a snapshot of the audience's code, name, and template, and
    the output language.
  - If a template cannot render, approval fails with
    `409 release_note_render_failed`.
- While a release is approved, notes can change in two ways:
  - A person may **edit a note's Markdown**. The note becomes manual and never
    follows its template again.
  - A person may **write or correct a change's summary and narratives** during
    review or after approval. This is recorded on the change with the editor
    and time, and the AI never overwrites it. Notes that still follow their
    template are rendered again from their stored templates.
- Returning a release to draft deletes its notes; approving again writes new
  ones. Publishing requires notes and freezes them.
- PostgreSQL enforces this alongside the service. `release_audience_notes`
  accepts inserts and updates only for approved releases, and only the content
  and edit record may change. Nothing can be updated or deleted once the
  release is published.
- **Legacy notes (D10).** The V8 `release_notes` table keeps the single note of
  releases published before V13. It is read-only and receives no new rows.
  Releases approved before V13 return to `IN_REVIEW` with their decisions
  kept, so that approving them writes their notes. No public version has been
  released.
- Markdown shown in pages is rendered on the server with CommonMark. Raw HTML
  is escaped and link targets are sanitized. Links get
  `rel="nofollow noopener noreferrer"`, and images become links, so a preview
  never loads remote content.

## Consequences

- Each change still costs exactly one AI request, and every audience's note
  states the same facts.
- Adding an audience needs no code change. The next AI request asks for its
  narrative, and the next approval writes its note.
- A note reflects the changes as they were at approval, together with later
  summary edits made on the release page. Correcting a classification in the
  Change Inbox after approval does not move a change between sections. To pick
  that up, return the release to draft and approve it again. This replaces the
  consequence in [ADR-0010](0010-release-review-lifecycle.md) that the note
  published afterwards reflects the correction.
- [ADR-0005](0005-immutable-release-note-snapshots.md) still holds for
  published content. The snapshot is now one note per audience, written at
  approval rather than at publication.
- `change` and `release` now depend on the new `audience` package. `account`
  does not; it publishes an `OrganizationRegistered` event that `audience`
  handles inside the registration transaction.
- Translation into several languages, variants of a template per language, and
  asynchronous generation remain future decisions. Notes are written in the
  Organization's output language only.
