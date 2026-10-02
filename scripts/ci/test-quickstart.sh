#!/usr/bin/env bash
# Runs a packaged quickstart bundle the way a person would, and checks what quickstart.sh
# promises: a taken port stops it before .env is written; the first run starts a healthy
# application on loopback with secrets it never prints; a later run keeps .env and ignores
# the shell; and a database whose .env is gone is refused rather than reopened with new
# secrets. Linux only (GNU stat and xargs); CI runs it after package-quickstart.sh.
#
#   ./scripts/ci/package-quickstart.sh releaseflow:demo /tmp/bundle
#   ./scripts/ci/test-quickstart.sh /tmp/bundle
#
# The port defaults to 18080, so it runs beside the demo stack or a local application on
# 8080, and so the first run goes through the non-default port path.
set -euo pipefail

bundle="$(cd "${1:?usage: test-quickstart.sh <bundle-directory>}" && pwd)"
port="${QUICKSTART_TEST_PORT:-18080}"
readonly project="releaseflow-quickstart"
readonly volume="releaseflow-quickstart-postgres-data"
readonly app_container="${project}-app-1"

fail() { echo "not ok - $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

project_containers() {
  docker ps --all --quiet --filter "label=com.docker.compose.project=${project}"
}

# The cleanup below deletes the quickstart database, so never start next to a real one.
if docker volume inspect "${volume}" >/dev/null 2>&1 || [ -n "$(project_containers)" ]; then
  echo "A quickstart stack or its database (${volume}) already exists here. This test deletes" \
    "both when it finishes, so remove them yourself first." >&2
  exit 1
fi

work="$(mktemp -d)"
listener=""
cleanup() {
  local status=$?
  if [ "${status}" -ne 0 ]; then
    docker logs "${app_container}" 2>&1 | tail -n 200 >&2 || true
  fi
  if [ -n "${listener}" ]; then
    kill "${listener}" 2>/dev/null || true
  fi
  project_containers | xargs --no-run-if-empty docker rm --force >/dev/null
  docker volume rm --force "${volume}" >/dev/null 2>&1 || true
  docker network rm "${project}_default" >/dev/null 2>&1 || true
  rm -rf "${work}"
  exit "${status}"
}
trap cleanup EXIT

fresh_copy() {
  local dir="${work}/$1"
  mkdir -p "${dir}"
  cp "${bundle}/compose.yaml" "${bundle}/quickstart.sh" "${dir}/"
  echo "${dir}"
}

answers() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

application_is_up() {
  curl --fail --silent --show-error "http://127.0.0.1:${port}/api/status" \
    | jq -e '.status == "UP"' >/dev/null
}

# 1. A taken port stops the first run before it writes anything.
dir="$(fresh_copy busy)"
python3 -m http.server "${port}" --bind 127.0.0.1 >/dev/null 2>&1 &
listener=$!
for _ in $(seq 50); do
  answers "${port}" && break
  sleep 0.1
done
answers "${port}" || fail "the stand-in listener never opened port ${port}"
if RELEASEFLOW_HTTP_PORT="${port}" bash "${dir}/quickstart.sh" >"${work}/busy.log" 2>&1; then
  fail "started although port ${port} was taken"
fi
grep -qF "port ${port} is in use" "${work}/busy.log" || fail "did not say port ${port} is in use"
[ ! -e "${dir}/.env" ] || fail "wrote .env although it could not start"
kill "${listener}"
wait "${listener}" 2>/dev/null || true
listener=""
pass "a taken port stops the first run before .env is written"

# 2. The first run writes a private .env, starts on loopback, and prints no secret.
dir="$(fresh_copy first)"
RELEASEFLOW_HTTP_PORT="${port}" bash "${dir}/quickstart.sh" 2>&1 | tee "${work}/first.log"
application_is_up || fail "the application is not up after the first run"
[ "$(stat -c %a "${dir}/.env")" = "600" ] || fail ".env can be read by others"
for key in RELEASEFLOW_DB_PASSWORD RELEASEFLOW_CREDENTIAL_MASTER_KEY; do
  value="$(sed -n "s/^${key}=//p" "${dir}/.env")"
  [ -n "${value}" ] || fail "${key} is not in .env"
  if grep -qF -- "${value}" "${work}/first.log"; then
    fail "printed ${key}"
  fi
done
grep -qxF "RELEASEFLOW_PUBLIC_BASE_URL=http://localhost:${port}" "${dir}/.env" \
  || fail "the public address in .env does not follow the port"
grep -qF "http://localhost:${port}/register" "${work}/first.log" \
  || fail "did not print where to register"
[ "$(docker port "${app_container}" 8080/tcp)" = "127.0.0.1:${port}" ] \
  || fail "the application is published somewhere other than 127.0.0.1:${port}"
[ -z "$(docker port "${project}-db-1")" ] || fail "the database port is published"
pass "the first run starts on loopback with a private .env and prints no secret"

# 3. A later run keeps .env and ignores what the shell says. A wrong database password
#    from the shell would recreate the application unable to connect, and the COMPOSE_*
#    variables would send Compose to another project and file.
before="$(sha256sum "${dir}/.env")"
RELEASEFLOW_DB_PASSWORD=wrong RELEASEFLOW_HTTP_PORT=1 \
  COMPOSE_PROJECT_NAME=elsewhere COMPOSE_FILE=/nonexistent/compose.yaml \
  COMPOSE_ENV_FILES=/nonexistent/.env \
  bash "${dir}/quickstart.sh" 2>&1 | tee "${work}/again.log"
[ "$(sha256sum "${dir}/.env")" = "${before}" ] || fail "the second run changed .env"
grep -qF "Keeping the existing .env." "${work}/again.log" || fail "did not say it kept .env"
application_is_up || fail "the application is not up after the second run"
[ -z "$(docker ps --all --quiet --filter label=com.docker.compose.project=elsewhere)" ] \
  || fail "COMPOSE_PROJECT_NAME from the shell started another project"
pass "a later run keeps .env and ignores RELEASEFLOW_* and COMPOSE_* from the shell"

# 4. A database whose .env has gone is refused, and no new .env is written for it.
mv "${dir}/.env" "${work}/saved.env"
if bash "${dir}/quickstart.sh" >"${work}/lost.log" 2>&1; then
  fail "started a database whose .env is gone"
fi
grep -qF "docker volume rm ${volume}" "${work}/lost.log" || fail "did not say how to delete the data"
[ ! -e "${dir}/.env" ] || fail "wrote a new .env for an existing database"
mv "${work}/saved.env" "${dir}/.env"
pass "a database whose .env is gone is refused"
