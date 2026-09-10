# ADR-0002: Per-integration webhook credentials

- Status: Accepted
- Date: 2026-09-10

## Context

ReleaseFlow must route a future GitHub webhook to the correct Organization
before trusting payload content. A shared webhook identifier or signing secret
would let knowledge from one connection affect another tenant. Plaintext
storage would also expose all connected Organizations if the database leaked.

## Decision

Assign every GitHub integration a globally unique webhook UUID and a separately
generated 32-byte signing secret. Reveal the plaintext secret only in the
successful creation response; Project reads and conflict responses never return
it.

Encrypt the secret with AES-256-GCM before persistence. Store the random 12-byte
nonce and authenticated ciphertext separately. Bind Organization ID, Project
ID, integration ID, repository owner, and repository name as additional
authenticated data. Obtain the Base64-encoded 32-byte master key exclusively
from `RELEASEFLOW_CREDENTIAL_MASTER_KEY` and fail application startup if it is
missing or invalid.

## Consequences

- A future webhook can resolve tenant and connection from the untrusted URL
  path, then decrypt the connection-specific key before HMAC verification.
- Database disclosure alone does not reveal signing secrets, and moving
  ciphertext between integrations invalidates authentication.
- Operators must securely retain the master key and treat losing it as loss of
  access to stored webhook credentials.
- Key rotation, secret rotation, and integration replacement need explicit
  future workflows and are not pre-built in this slice.
