# Contributing to ReleaseFlow

ReleaseFlow is developed as small, reviewable vertical slices. Every change
must preserve the product invariants documented in `AGENTS.md` and keep the
documentation aligned with the code.

## Branch model

- `main` contains reviewed, releasable code. Direct development does not happen
  on this branch.
- `develop` is the integration branch for the next releasable state.
- `feature/<kebab-case-name>` branches from `develop` for one vertical slice or
  one independently reviewable capability.
- `fix/<kebab-case-name>` branches from `develop` for non-production defects.
- `release/<version>` branches from `develop` only when a real release is being
  stabilized.
- `hotfix/<version>` branches from `main` only for an urgent production fix and
  is merged back into both `main` and `develop`.

Do not create speculative feature or release branches. Create the next branch
only when its work begins, and delete short-lived branches after merge.

## Commit convention

Commits follow Conventional Commits:

```text
<type>(<scope>): <imperative summary>
```

Use one of these types:

- `feat`: user-visible capability;
- `fix`: defect correction;
- `docs`: documentation-only change;
- `test`: test-only change;
- `refactor`: behavior-preserving code change;
- `build`: build system or dependency change;
- `ci`: continuous-integration change;
- `chore`: repository maintenance not covered above.

Keep the summary lowercase, imperative, and without a trailing period. Add a
body when the reason or trade-off is not evident. Use `BREAKING CHANGE:` in the
footer only for an intentional incompatible contract change.

Examples:

```text
feat(account): register organization owner
fix(webhook): reject repository identity mismatch
docs(architecture): record tenant isolation decision
```

## Pull request quality gate

Before requesting review:

1. Rebase the branch on the current `develop` branch.
2. Run `./mvnw test` and `./mvnw verify` with JDK 21.
3. Review the complete diff for credentials, generated files, and unrelated
   changes.
4. Update `README.md`, `CHANGELOG.md`, `docs/architecture.md`, and
   `docs/implementation-status.md` when behavior changes.
5. Prefer a squash merge so the resulting integration commit also follows the
   commit convention.

GitHub Actions enforces the automated part of this gate on every pull request
to `develop` and `main`: the pull request title must follow the commit
convention above, and the `CI`, `CodeQL`, `Dependency Review`, `Secret Scan`,
and `Container` workflows must pass. Keep new actions pinned to a commit SHA
and new images to a digest.

By contributing, you agree that your contribution is licensed under the
[Apache License 2.0](LICENSE), the same licence as the rest of the project. There
is no separate contributor agreement to sign.

Merges into `main` represent a reviewed release decision. Tags, release
artifacts, and deployment automation are added only when a real publication
process exists.
