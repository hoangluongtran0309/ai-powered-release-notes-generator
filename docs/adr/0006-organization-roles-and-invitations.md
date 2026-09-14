# ADR-0006: Organization roles and invitations

- Status: Accepted
- Date: 2026-09-14

## Context

Until now an Organization had exactly one account, created at registration
with the `OWNER` role. Teams need several people in one workspace, and some
actions, such as connecting a repository (which creates a webhook signing
secret), should stay with administrators. Adding people is a security boundary:
an invitation link grants access to a tenant, so it must not leak through
storage, logs, browser history, or referrers.

## Decision

- Accounts have one of two roles. `ADMIN` is created at registration and by
  migration from `OWNER`. `MEMBER` is created only by accepting an invitation.
  Administrators manage members and invitations and configure GitHub
  integrations. Members can do everything else, including reviewing changes and
  preparing and publishing releases.
- Role checks happen twice: in the Spring Security URL rules and again in
  `InvitationService`.
- An administrator invites one email address at a time. The token is 32 random
  bytes encoded as base64url. Only its SHA-256 hash is stored, and the raw token
  is returned once, in a non-cacheable response. An invitation expires after
  `RELEASEFLOW_INVITATION_TTL` (seven days by default), can be used once, and can
  be reissued or revoked. An Organization has at most one pending invitation per
  email. An email that already has an account cannot be invited, because
  emails are globally unique.
- The link carries the token in the URL fragment,
  `/accept-invite#token=…`. Browsers never send the fragment to the server. A
  short script in `app.js` copies it into a server-rendered form, removes it
  from the address bar and history, and submits the form. Without JavaScript
  the invitee pastes the code. This is a deliberate exception to the rule that
  JavaScript only handles presentation, limited to this one hand-off.
- Acceptance happens in three server-rendered steps: open, review (who is
  inviting whom), and accept (name and password). The invitation row is locked,
  a `MEMBER` account is created in the invitation's Organization, and the
  invitation is marked accepted. Every unusable token — unknown, expired,
  revoked, or already used — gets the same response.
- Changing roles and removing members are not provided.

## Consequences

- Composite foreign keys keep the inviting and accepting accounts inside the
  invitation's Organization, and a database check keeps the invitation state
  consistent.
- A lost link cannot be recovered. The administrator reissues it instead.
- Because the principal caches the role at sign-in, future role changes would
  need to invalidate sessions. This is one reason role changes were left out.
- The acceptance path depends on a small amount of JavaScript for convenience,
  but works without it.
