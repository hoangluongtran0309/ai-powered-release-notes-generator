#!/usr/bin/env bash
#
# Starts ReleaseFlow from a release's quickstart bundle: this script and the compose.yaml
# beside it. The first run writes .env with new secrets; every later run keeps it.
#
#   bash quickstart.sh                              # http://localhost:8080
#   RELEASEFLOW_HTTP_PORT=9090 bash quickstart.sh   # another port, chosen on the first run
#
# It installs nothing, asks for no privilege, and asks no questions. It is written for
# bash 3.2, so macOS runs it as shipped; on Windows, run it inside WSL 2.

if [ -z "${BASH_VERSION:-}" ]; then
  echo "quickstart: run this with bash: bash quickstart.sh" >&2
  exit 1
fi

set -euo pipefail

readonly PROJECT="releaseflow-quickstart"
readonly VOLUME="releaseflow-quickstart-postgres-data"

here="$(cd "$(dirname "$0")" && pwd)"
readonly here
readonly env_file="${here}/.env"

tmp_env=""
trap '[ -z "${tmp_env}" ] || rm -f "${tmp_env}"' EXIT

say() { printf '%s\n' "$*"; }
fail() { printf 'quickstart: %s\n' "$*" >&2; exit 1; }

# Read before the environment is cleared below. Only the run that writes .env uses it.
shell_port="${RELEASEFLOW_HTTP_PORT:-}"

# .env is the only configuration. Compose prefers a shell variable to .env, so a
# RELEASEFLOW_* exported for another way of running ReleaseFlow would quietly win over it,
# and a COMPOSE_* could point Compose at another file or project altogether.
ignored=""
for name in $(compgen -e); do
  case "${name}" in
    RELEASEFLOW_HTTP_PORT) unset "${name}" ;;
    RELEASEFLOW_*) ignored="${ignored} ${name}"; unset "${name}" ;;
    COMPOSE_*) unset "${name}" ;;
  esac
done
if [ -n "${ignored}" ]; then
  say "Ignoring${ignored} from the shell: .env is the only configuration."
fi

compose() {
  docker compose --project-name "${PROJECT}" --project-directory "${here}" \
    --file "${here}/compose.yaml" --env-file "${env_file}" "$@"
}

valid_port() {
  case "$1" in
    '' | *[!0-9]* | ??????*) return 1 ;;
  esac
  [ "$1" -ge 1 ] && [ "$1" -le 65535 ]
}

# Something answers on the loopback port, which is where Compose publishes the application.
port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

write_env() {
  local port="$1" db_password master_key
  db_password="$(openssl rand -hex 24)"
  master_key="$(openssl rand -base64 32)"
  if [ -z "${db_password}" ] || [ -z "${master_key}" ]; then
    fail "openssl did not generate the secrets."
  fi
  # Written beside .env and moved into place, so an interrupted run never leaves half a file.
  tmp_env="$(mktemp "${here}/.env.XXXXXX")"
  chmod 600 "${tmp_env}"
  {
    say "# Written by quickstart.sh on $(date -u +%Y-%m-%d). Keep this file, and keep it private."
    say "# The database was created with this password, and this key encrypts every stored"
    say "# webhook secret and access token: without them, the data cannot be opened again."
    say "RELEASEFLOW_DB_PASSWORD=${db_password}"
    say "RELEASEFLOW_CREDENTIAL_MASTER_KEY=${master_key}"
    say ""
    say "# Change both together, then run quickstart.sh again."
    say "RELEASEFLOW_HTTP_PORT=${port}"
    say "RELEASEFLOW_PUBLIC_BASE_URL=http://localhost:${port}"
    say ""
    say "# Optional AI classification: choose openai, anthropic, or deepseek, fill in that"
    say "# provider's key and model (there is no default model), and run quickstart.sh again."
    say "# RELEASEFLOW_AI_PROVIDER="
    say "# RELEASEFLOW_OPENAI_API_KEY="
    say "# RELEASEFLOW_OPENAI_MODEL="
    say "# RELEASEFLOW_ANTHROPIC_API_KEY="
    say "# RELEASEFLOW_ANTHROPIC_MODEL="
    say "# RELEASEFLOW_DEEPSEEK_API_KEY="
    say "# RELEASEFLOW_DEEPSEEK_MODEL="
    say ""
    say "# Translation and email settings are listed in the README's Quickstart section."
  } > "${tmp_env}"
  mv "${tmp_env}" "${env_file}"
  tmp_env=""
}

command -v docker >/dev/null 2>&1 \
  || fail "Docker is not installed. Install Docker Engine or Docker Desktop: https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 \
  || fail "the Docker Compose v2 plugin ('docker compose') is missing: https://docs.docker.com/compose/install/"
# Read into a variable rather than piped into grep -q, which can stop reading early and
# fail the pipeline under pipefail.
up_help="$(docker compose up --help 2>/dev/null || true)"
case "${up_help}" in
  *--wait-timeout*) ;;
  *) fail "this Docker Compose cannot wait for the application to be healthy. Update Docker." ;;
esac
docker info >/dev/null 2>&1 \
  || fail "cannot reach the Docker daemon. Start Docker, or give your user access to it: https://docs.docker.com/engine/install/linux-postinstall/"
[ -f "${here}/compose.yaml" ] \
  || fail "compose.yaml must sit beside quickstart.sh in ${here}."
if grep -q 'RELEASEFLOW_IMAGE' "${here}/compose.yaml"; then
  fail "this compose.yaml is the repository's template. Use the one attached to a release."
fi

first_run="false"
if [ -f "${env_file}" ]; then
  port="$(sed -n 's/^RELEASEFLOW_HTTP_PORT=//p' "${env_file}" | tail -n 1)"
  # Read the way Compose reads it: a line ending saved on Windows, and quotes, are not part
  # of the value.
  port="${port%$'\r'}"
  port="${port#[\"\']}"
  port="${port%[\"\']}"
  port="${port:-8080}"
  valid_port "${port}" || fail "RELEASEFLOW_HTTP_PORT in .env is not a port: ${port}"
  if [ -n "${shell_port}" ] && [ "${shell_port}" != "${port}" ]; then
    say "RELEASEFLOW_HTTP_PORT is read only on the first run; .env says ${port}."
  fi
  running="$(compose ps --status running --quiet app 2>/dev/null || true)"
  if [ -z "${running}" ] && port_in_use "${port}"; then
    fail "port ${port} is in use. Change RELEASEFLOW_HTTP_PORT and RELEASEFLOW_PUBLIC_BASE_URL in .env to a free port, then run this again."
  fi
  say "Keeping the existing .env."
else
  # A database whose .env is gone was created with a password, and holds secrets encrypted
  # with a key, that only that .env knew. New ones would open neither.
  if docker volume inspect "${VOLUME}" >/dev/null 2>&1; then
    fail "a ReleaseFlow database (${VOLUME}) already exists, but there is no .env in ${here}. Its password and the key that encrypts its secrets were in that .env. Put it back, or delete the database for good with: docker volume rm ${VOLUME}"
  fi
  port="${shell_port:-8080}"
  valid_port "${port}" || fail "RELEASEFLOW_HTTP_PORT is not a port: ${port}"
  if port_in_use "${port}"; then
    fail "port ${port} is in use. Choose a free one, for example: RELEASEFLOW_HTTP_PORT=9090 bash quickstart.sh"
  fi
  command -v openssl >/dev/null 2>&1 \
    || fail "openssl is needed to generate the secrets in .env."
  write_env "${port}"
  first_run="true"
  say "Wrote .env with a new database password and credential master key. Keep it: without it, this database cannot be opened again."
fi

if [ "${first_run}" = "true" ]; then
  say "Starting ReleaseFlow. The first start downloads the images and prepares the database, which can take a few minutes."
else
  say "Starting ReleaseFlow."
fi
if ! compose up --detach --wait --wait-timeout 300; then
  compose ps >&2 || true
  fail "ReleaseFlow did not become healthy. See why with: docker compose logs app (in ${here})"
fi

cat <<EOF

ReleaseFlow is running at http://localhost:${port}
Create the first administrator at http://localhost:${port}/register

Run these in ${here}:
  docker compose ps               what is running
  docker compose logs -f app      the application's log
  docker compose down             stop; the data stays
  docker compose down --volumes   stop and delete the database for good
  bash quickstart.sh              start again, or apply a change made to .env
EOF
