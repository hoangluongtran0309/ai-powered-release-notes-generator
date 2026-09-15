# ADR-0014: Sensitive paths per Project

- Status: Accepted
- Date: 2026-09-15

## Context

A change that touches a file matching a sensitive-path pattern always needs
review. The patterns are a deployment setting (`RELEASEFLOW_SENSITIVE_PATHS`)
shared by every Project. A Project with its own sensitive area, such as
`billing/`, could only be covered by changing that setting for everyone.

## Decision

- **Additions only.**
  - Administrators add glob patterns to one Project.
  - The patterns a Project's changes are checked against are the baseline, in
    configured order, followed by the Project's additions, without repeats.
  - A Project can never remove a baseline pattern, and the baseline is read at
    classification time, so a changed deployment setting always applies.
- **Validation.**
  - Patterns are trimmed, and blank lines and repeats are dropped.
  - A Project can add at most 100 patterns of at most 256 characters. Each must
    compile as a JDK glob, and a pattern starting with `**/` also matches at
    the repository root, as the baseline does.
  - Anything else is rejected with `400 invalid_sensitive_paths`, and the saved
    patterns stay as they were.
- **Access.**
  - Every member can read a Project's patterns, so a reviewer can see why a
    change was flagged.
  - Only administrators change them, through the Sensitive paths page or
    `PUT /api/projects/{projectId}/sensitive-paths`.
  - The last administrator to change them and the time are recorded.
- **When they apply.**
  - Additions apply to changes classified afterwards: a new change, a job
    completed without AI, or an AI retry.
  - Changes already classified keep their triggers.
- **Trigger unchanged.** A match adds the same `SENSITIVE_PATH` trigger with
  the matched path, whether the pattern comes from the baseline or the Project.
- **Storage.** `project_sensitive_paths` (Flyway `V16`) holds one row per
  Project. No row means no additions, and clearing the list keeps the row as a
  record of who cleared it.

## Consequences

- A Project can protect its own sensitive areas without changing the setting
  for everyone, and cannot weaken the deployment's protection.
- A trigger does not say which pattern matched or where it came from. A
  reviewer reads the Project's list to see why.
- Adding a pattern does not re-check earlier changes. A change classified
  before the pattern existed is not flagged by it.
- The page lives in the `change` package, next to the baseline, and is its own
  page rather than a section of the Projects page, which cannot depend on
  `change`.
