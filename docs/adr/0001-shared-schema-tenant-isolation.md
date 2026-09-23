# ADR-0001: Shared-schema tenant isolation

- Status: Accepted
- Date: 2026-09-09

## Context

Organization is ReleaseFlow's tenant boundary. The first persistence slice must
establish isolation rules that later Project, Change, Classification, and
Release capabilities can apply consistently. Separate databases or schemas per
tenant would add provisioning and migration complexity that the current product
does not need. An implicit Hibernate tenant filter would make repository
behavior harder to see and easier to bypass in non-Hibernate access paths.

## Decision

Use one PostgreSQL schema shared by all Organizations. Tenant-owned rows carry
an explicit `organization_id`. Authenticated tenant identity comes from the
server-created principal; verified webhook identity will supply it for webhook
requests in a later slice. Request DTOs never accept a tenant ID.

Repository operations for tenant-owned resources must include
`organizationId` explicitly in their method or query, including lookups by a
globally unique entity ID. Database foreign keys and uniqueness constraints
remain a second line of enforcement. Negative cross-tenant tests are required
for every new tenant-owned capability.

## Consequences

- Tenant scope is visible at repository call sites and works with JPA or direct
  SQL.
- A missing tenant predicate remains possible, so code review and negative
  integration tests are mandatory.
- Operations spanning Organizations are not exposed through product services.
- A future move to schema- or database-per-tenant would require an explicit
  migration and is not pre-abstracted now.
