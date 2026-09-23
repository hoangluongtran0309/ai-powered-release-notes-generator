# ADR-0022: A public changelog

- Status: Accepted
- Date: 2026-09-20

## Context

Every way a release note left ReleaseFlow was a push to somewhere else: a GitHub
Release, a Slack channel, an inbox. The people a product is for are often in none of
those places. They want a page they can visit and a feed they can subscribe to.

That destination is different from the others in three ways, and each of them needed a
decision.

- **It is read by people with no account**, which means an unauthenticated surface, and
  it reads release notes — the material ReleaseFlow otherwise guards most carefully.
- **It is ours**, so unlike every other delivery a repeat is not a risk. Publishing the
  same note twice can be made to mean nothing at all, which no provider allows.
- **It needs a name in a URL.** ReleaseFlow had none: an Organization was only a UUID,
  and the old project's slug was never validated on the server (gap G8).

## Decision

- **An Organization has a slug: one DNS label.** Lowercase letters, digits, and hyphens,
  up to 63 characters, not starting or ending with a hyphen — checked in
  `OrganizationSlug.parse`, by a CHECK constraint, and unique across the deployment. One
  label rather than free text, because the same value has to work both as a path segment
  and as a subdomain. `V24` gives every existing Organization one made from its name,
  numbering collisions by age. Registration derives one the same way, and an
  administrator can change it afterwards — which moves the changelog and breaks links
  already shared, so the page says exactly that and only administrators may do it.
- **A public entry is an immutable snapshot, made only by a `PUBLIC_CHANGELOG` Action.**
  Publishing a release does not publish it to the world; a rule does, for one audience
  and language. The entry copies the Organization's name and slug, the Project's name,
  the version, the audience, the language, and the note itself, so renaming anything
  later never rewrites what people read. A trigger refuses every update and delete, as
  `release_notes` already does.
- **Publishing the same note again is free; publishing different words is refused.** The
  entry is unique per Organization, release, audience, and language, and per Action run.
  A retry after an unknown outcome finds its own entry and succeeds. A second rule that
  would publish different content for the same release, audience, and language fails as
  `public_changelog_conflict` rather than overwriting something a reader may already
  have seen. This is the only Action where a repeat is safe, and it is safe because the
  destination is ReleaseFlow's own database.
- **This Action alone touches no network.** The worker calls an executor with no
  transaction open precisely so a provider is never called inside one; this executor
  therefore opens its own short transaction instead. It is also refused on scheduled
  triggers, like every other Action that is not Slack or email: a cron rule would
  republish on a cycle, and a reminder works on a release that is not published yet.
- **The public surface has its own security chain.** `/changelog/**` is permitted for
  GET and nothing else, with the request cache disabled and no session created: a reader
  who has no account is never given a cookie for looking. The Organization comes from
  the slug in the path.
- **Markdown is rendered by the renderer the application already trusts.**
  `MarkdownHtml.render` escapes raw HTML, sanitizes link targets, marks links
  `nofollow noopener noreferrer`, and turns images into plain links so a page never
  loads anything from another host. The RSS `description` carries that same HTML, and
  the JDK's XML writer escapes it into the document. Nothing new parses Markdown here.
- **Pages are cached by what they are.** The list is `public, max-age=300`; an entry,
  which can never change, is `public, max-age=86400, immutable` with its identifier as
  the ETag, so a reader who has it is answered `304`. The feed carries at most the 50
  newest entries.
- **Absolute links come from configuration, never from the request.**
  `releaseflow.public.base-url` is the deployment's own address, and
  `releaseflow.public.changelog-base-domain` optionally turns on
  `{slug}.{domain}`. A filter rewrites exactly three paths for exactly one label beneath
  that domain, for GET only, before Spring Security sees the request. A `Host` header
  chooses which slug to look up and decides nothing else; it never becomes a link
  ReleaseFlow writes, and wildcard DNS and TLS are the deployment's own to provision.

## Consequences

- An Organization is now nameable from outside. That is a small but real disclosure:
  anybody who guesses a slug learns whether it answers. They learn nothing further —
  a slug with no entries looks exactly like an Organization that has published nothing.
- A changelog page is the first thing ReleaseFlow serves that is meant to be cached by
  intermediaries, and the first response with an ETag.
- `automation_action_runs` gains the composite key `(id, organization_id)` every other
  tenant-owned table already had, because an entry now points at the run that made it
  and must be tied to the same Organization.
- Deleting an Organization's automation history is no longer enough to erase what it
  published: an entry outlives the run, on purpose.
- A future slice that wants a changelog per Project, or a page a reader can search, has
  the entry table to build on without touching releases.
