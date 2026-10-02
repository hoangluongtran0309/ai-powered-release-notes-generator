# ADR-0031: Quickstart bundle

- Status: Accepted
- Date: 2026-10-02
- Builds on: [ADR-0030](0030-release-automation.md), which publishes the image this
  bundle runs, and [ADR-0007](0007-ci-and-container-supply-chain.md), whose pinning and
  checksum rules it follows.

## Context

Trying a released ReleaseFlow meant cloning the repository, copying `.env.example`,
generating three secrets by hand, and running the demo stack with `--build`, which
compiles the whole application, downloads Node.js, and starts Prometheus and Grafana
besides. The image every release pushes to GHCR, public and pinned by digest, went
unused on that path.

A script could do the typing, but in this application a careless one destroys data. The
database is created with the password `.env` names, and every webhook secret and access
token is encrypted with the master key beside it. A script that writes new secrets over an
old `.env`, or next to a database whose `.env` was lost, leaves a database nothing can
open. And Compose prefers a variable from the shell to the one in `.env`, so a person who
exported `RELEASEFLOW_DB_PASSWORD` to run the application another way would create the
database with that password instead, without being told.

## Decision

- **The bundle is two files on every release: `compose.yaml` and `quickstart.sh`.** They are
  attached beside the JAR, listed in `SHA256SUMS`, and covered by the same provenance
  attestation. A person downloads them, checks them, and runs them. There is no
  `curl | bash`, because the point of checksums is to look before running.
- **The image is pinned by tag and digest, written in after the push.** The template in
  `quickstart/compose.yaml` leaves the image as an unset variable, so the repository's
  copy refuses to start rather than run something unpinned. `package-quickstart.sh`
  renders it; the Release workflow calls it with the digest the registry returned, and the
  Container workflow with the image it just built, so CI tests exactly what ships.
- **The application and PostgreSQL, nothing else.** Loopback only, the database
  unpublished, no bind mounts, so nothing in it depends on a checkout or on SELinux labels.
  Observability stays in the demo stack. The bundle forwards the demo's variables, less
  the management address only Prometheus needs, and CI fails when the two lists differ.
- **`.env` is the only configuration.** The script clears every `RELEASEFLOW_*` and
  `COMPOSE_*` variable it inherits, after reading `RELEASEFLOW_HTTP_PORT` for a first run,
  and passes Compose its project, directory, file, and env file explicitly.
- **The script never overwrites `.env`, and refuses a database whose `.env` is gone.** The
  first run writes it with `openssl`, mode 0600, through a temporary file moved into place,
  and prints no secret. A later run keeps it as it is. When the fixed-name database volume
  exists and `.env` does not, the script stops and names the two ways out: put the old file
  back, or delete the data.
- **It installs nothing, asks for no privilege, and asks no questions.** A missing Docker,
  Compose without `--wait-timeout`, an unreachable daemon, or a taken port each stop it with
  the fix. AI keys and other settings go into `.env`, and running the script again applies
  them. Being non-interactive is also what lets CI run it.
- **bash 3.2 on Linux and macOS; Windows through WSL 2.** CI runs the script against a real
  Docker on Linux, and runs it again from start to finish under bash 3.2, the version macOS
  ships, with stand-ins for `docker` and `openssl`: `bash -n` would accept a `${x,,}` that
  only fails when it runs.

## Consequences

- The quickstart exists from the first release after 0.1.1. Until a tag publishes one, the
  README says so rather than pointing at files that are not there.
- Checksums are written after the push now, because `compose.yaml` cannot be written
  before the registry names the digest.
- The application's environment is written out twice, once per Compose file. The CI check
  makes a forgotten variable a failed build rather than a quickstart that silently ignores
  a setting.
- The published image is amd64 only, so Apple silicon runs it under emulation. A
  multi-architecture image is a separate change to the Release workflow.
- macOS is covered by discipline and by bash 3.2 in CI, not by a macOS runner. A difference
  in BSD tools outside the script's own small set would not be caught.
- The bundle is for one machine. TLS, backups, and secret storage stay a real deployment's
  own to bring, as the demo stack's do.
