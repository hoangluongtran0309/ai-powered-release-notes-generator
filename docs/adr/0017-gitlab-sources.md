# ADR-0017: GitLab sources and provider seams

- Status: Accepted
- Date: 2026-09-16

## Context

ADR-0016 gave a Project several integration sources, but GitHub was still the
only kind: `SourceType` had one value, and `GitHubApiClient` and
`GitHubRepositoryAccess` were wired straight into the source setup, the change
worker, and the history import. GitLab is the next source, and unlike GitHub it
is not one address: an Organization may run its own instance. It also proves its
deliveries in two different ways, depending on how the webhook was set up.

## Decision

- **Two provider seams, no more.** The `change` capability owns two small ports,
  `ChangedFileCollector` and `SourceHistoryReader`, each with one implementation
  per source type and a registry that fails at startup if a type has none. The
  workers keep every decision they already made — retries, the item limit, the
  `page:offset` cursor, stale recovery — and only the provider call, the
  "an older item ends the scan" rule, and the error code vary.
- **A `source` package for the shared vocabulary.** `SourceType`,
  `ChangedFile(Kind)`, `ChangedFiles`, `ProviderListing`, `ProviderAccess`,
  `WebhookAuthMode`, and `SourceCredentials` moved out of `github`, so `github`
  and `gitlab` are adapters that depend on the vocabulary rather than on each
  other. The stored failure codes did not change.
- **A GitLab project is named only by its path.** `external_project_key` holds
  `group/project`, with any number of subgroups; `repository_owner` and
  `repository_name` became nullable and are GitHub's alone. A key is unique per
  Organization *and* source type, so the same name on both providers is two
  sources.
- **The instance is allowlisted, not free.** A GitLab source carries an
  `api_base_url`, the only provider address a request may choose. `GitLabBaseUrl`
  accepts it only if it is a plain HTTP or HTTPS address — no credentials, query,
  or fragment — whose origin is in `releaseflow.gitlab.allowed-hosts`
  (`gitlab.com` by default). An entry is `host` or `host:port`, meaning HTTPS, or
  a full origin such as `http://gitlab.internal:8080`; writing the scheme is the
  only way a deployment accepts an instance reached without TLS. The allowlist is
  checked again before every call, and redirects are never followed, so a
  redirect cannot move a call to another host.
- **One secret, two ways to prove a delivery.** ReleaseFlow generates the secret
  and reveals it once, exactly as for GitHub, and the administrator pastes it
  into GitLab. `webhook_auth_mode` records which check applies:
  `GITLAB_SIGNING_TOKEN` verifies the Standard Webhooks headers — `webhook-id`,
  `webhook-timestamp`, and a `webhook-signature` of `id.timestamp.body`, any one
  of several space-separated candidates, within five minutes of the clock — and
  `GITLAB_SECRET_TOKEN` compares `X-Gitlab-Token` with the secret in constant
  time. Every failure answers `401 webhook_signature_invalid`.
- **A GitLab change may have no delivery ID.** GitLab identifies a delivery only
  in its signing mode, and not always with a GUID, so a change records
  `X-Gitlab-Event-UUID` when it is one and nothing otherwise. The database now
  only requires that an imported change has no delivery ID.
- **A withheld diff is unavailable, never empty.** The changed files come from
  the merge request's diffs, at most ten pages of a hundred. A diff GitLab marks
  `collapsed`, `too_large`, or `truncated` makes the whole list unavailable, so a
  file that was never seen is never declared safe.
- **GitLab is read in the other direction.** The import asks for the merge
  requests updated after the window's start, least recently updated first, so the
  list only grows at its end and a resumed import reads the same page again.

## Consequences

- One Project can gather GitHub repositories and GitLab projects, and both reach
  the same intake, classification, AI, review, and release notes.
- A deployment decides which GitLab instances exist. A self-managed instance
  needs an entry in `RELEASEFLOW_GITLAB_ALLOWED_HOSTS`, and removing one stops
  its sources from being called at all.
- A squashed or fast-forwarded merge request has no merge commit, so the commit a
  change records falls back to the squash commit and then to the head commit.
  A webhook delivery carries no merge time, so the last update stands in for it.
- Linear and Jira will add their own `SourceType`, their own implementation of
  each port, and their own columns; neither worker should need to change again.
- Commit messages, which the old project read to find Jira keys, are not
  collected yet. They arrive with Jira enrichment, for both providers at once.
