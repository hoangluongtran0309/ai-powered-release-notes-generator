# ADR-0016: Integration sources and history import

- Status: Accepted
- Date: 2026-09-15

## Context

A Project had exactly one GitHub integration, and changes arrived only by
webhook. A product built from several repositories could not be one Project,
and pull requests merged before a repository was connected never reached
ReleaseFlow. GitLab, Linear, and Jira sources are planned next, so the model
needs a general notion of a source.

## Decision

- **Sources, renamed in place.**
  - `github_integrations` becomes `integration_sources`, with a
    `source_type` (`GITHUB` for now), an `external_project_key` (`owner/repo`),
    a connection status, and the last sync time and error code.
  - Rows keep their IDs, webhook IDs, repository names, and ciphertexts. The
    authenticated data of every secret and token is therefore unchanged,
    nothing is re-encrypted, and existing webhook paths keep working.
- **Several sources per Project.** A repository still belongs to one Project
  per Organization.
- **Changes identified by source.**
  - A change records its `source_id` and `external_id` (the pull request
    number for GitHub); `(source_id, external_id)` is unique.
  - `pull_request_number` stays for display. The Project-wide uniqueness of
    pull request numbers is dropped, so two repositories may both have #12.
  - Changes also record their `origin`, `WEBHOOK` or `IMPORT`. Only a webhook
    change has a delivery ID.
- **Project-scoped API.** `/api/projects/{projectId}/sources` replaces the
  former `/github-integration` endpoints. No public version used them.
- **History import.**
  - An administrator starts an import of a source with a token; it never starts
    by itself.
  - A durable job reads closed pull requests, most recently updated first,
    and records those merged in the last 90 days through the same intake as
    webhooks. Imported changes go through the same processing, AI included.
  - The job saves a `page:offset` cursor after every page. It stops at the first
    pull request updated before its window, and after 500 new changes, as
    `PARTIAL`. Resuming continues from the cursor with a fresh count.
  - It honours `Retry-After` and the rate-limit reset (at most an hour), backs
    off on other transient failures, fails after five attempts or at once on a
    refused token, and marks the source as failing when the token is refused.
  - One job per source may be under way, enforced by a partial unique index.
    A job left running for ten minutes is resumed.

## Consequences

- One Project can gather several repositories, and a new repository can bring
  its recent history.
- Imports and webhooks share one intake, so they never duplicate each other.
  An import may cost one AI request per imported change.
- The list is ordered by update time. A pull request updated during an import
  can move between pages and be skipped; webhooks cover what is merged after
  connection, and another import picks up the rest.
- GitLab, Linear, and Jira add their own source types, and columns for their
  credentials and polling, in later slices.
