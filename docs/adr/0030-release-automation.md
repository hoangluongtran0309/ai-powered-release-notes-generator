# ADR-0030: Release automation

- Status: Accepted
- Date: 2026-09-23
- Completes: `S24`, whose other half — the open-core/enterprise module split — was
  abandoned in [ADR-0029](0029-one-module-and-the-apache-licence.md).
- Amends: [CONTRIBUTING.md](../../CONTRIBUTING.md), which said tags and release artifacts
  would be added "only when a real publication process exists". One now does.

## Context

Nothing had ever been published. `main` held only the bootstrap commit, the POM said
`0.1.0-SNAPSHOT`, and every capability lived on `develop`. Cutting a release by hand would
have meant building on somebody's laptop and uploading the result, which is the one build
nobody can redo and nobody can check.

## Decision

- **A tag is the release, and the only input.** Pushing `v<major>.<minor>.<patch>` runs the
  workflow against exactly that commit. There is no version input to get wrong and no
  branch to be on by accident.
- **Three guards refuse a tag that does not describe a reviewed release**, before anything
  is built:
  - the tagged commit must be an ancestor of `main`, so nothing unmerged is published;
  - the POM version must equal the tag without its `v`, so a release is never named one
    thing and built as another;
  - a `-SNAPSHOT` is refused outright.
  A tag pushed by mistake therefore fails rather than ships, and deleting it is enough.
- **The suite runs again on the tagged commit.** It already ran on the pull request that
  merged it. A release is the one build nobody gets to redo, so it is worth the minutes.
- **One SBOM, of the image.** CycloneDX, produced by the Trivy that already scans the
  image with a checksum-verified binary. The image is what the JAR ships inside, so an
  SBOM of it lists the Java dependencies and the base image's own packages together — a
  JAR-only SBOM would describe less than what is actually deployed. This also avoids
  adding a Maven plugin that would run on every build for the sake of one build a year.
- **The image is tagged `X.Y.Z`, `X.Y`, `X` and `latest`, and the release names its
  digest.** A deployment can pin as tightly as it wants to; the release notes show the
  digest, because that is the only tag that cannot move.
- **Provenance is attested for both the image and the JAR**, so somebody who downloaded
  either can ask GitHub which workflow, at which commit, produced it.
- **The checksum file names its inputs rather than globbing them.** Whether a glob sees the
  file the shell is about to write depends on expansion order; naming them removes the
  question.

## Consequences

- Publishing needs no credential of anybody's: `GITHUB_TOKEN` pushes to GHCR under the
  repository's own namespace, and the attestation is signed with the workflow's identity.
- The workflow builds twice — once for `verify`, once inside `docker build`. That is slower
  than sharing the JAR between them, and it is what keeps the image reproducible from the
  Dockerfile alone rather than from whatever happened to be in `target/`.
- `latest` moves on every release. A deployment that follows it is asking to be upgraded;
  the README says to pin the digest instead.
- The first tag can only be pushed once `main` carries the release commit, so the order is
  fixed: merge, then tag. That is the point.
