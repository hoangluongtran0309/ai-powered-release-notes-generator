## What changes

<!-- What a reviewer or a user would notice, in a few sentences. -->

## Why

<!-- The problem this solves. Link the issue if there is one: Closes #... -->

## Checklist

- [ ] The pull request targets `develop` and its title follows the commit convention in `CONTRIBUTING.md`
- [ ] `./mvnw verify` passes with JDK 21
- [ ] A changed page keeps `npm run e2e` green, accessibility included
- [ ] `README.md`, `CHANGELOG.md`, `docs/architecture.md`, and `docs/implementation-status.md` match the new behavior
- [ ] No credentials, tokens, or generated files in the diff
