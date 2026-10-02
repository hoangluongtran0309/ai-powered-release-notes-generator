#!/usr/bin/env bash
# Runs quickstart.sh from start to finish under bash 3.2, the version macOS ships, with
# stand-ins for docker and openssl. test-quickstart.sh proves what the script does against
# a real Docker on Linux; this proves bash 3.2 can execute every line on the way there,
# which `bash -n` cannot: an expansion such as ${x,,} parses and only fails when it runs.
# Nor is the exit status enough: bash 3.2 stops at a bad substitution and can still exit 0,
# so every run is judged by what it printed and what it wrote.
#
#   ./scripts/ci/package-quickstart.sh releaseflow:check /tmp/bundle
#   ./scripts/ci/test-quickstart-bash32.sh /tmp/bundle
set -euo pipefail

readonly BASH32_IMAGE="bash:3.2@sha256:0fd7cb8499c63a3c9345e7088a9cd83bb69f6e895e83833859aff838a0312091"
# Stand-in secrets. Nothing here decodes them, and real-looking ones would be findings for
# the secret scan.
readonly STUB_PASSWORD="stub-database-password"
readonly STUB_KEY="stub-credential-master-key"

bundle="$(cd "${1:?usage: test-quickstart-bash32.sh <bundle-directory>}" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

fail() { echo "not ok - $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

# Fails, showing the run's output, unless that output has the line or text given.
expect_output() {
  grep -qF -- "$2" "$1" || { cat "$1" >&2; fail "$3"; }
}

mkdir -p "${work}/stubs"
# Answers what quickstart.sh asks of Docker, as a running daemon with no quickstart
# database would, and records every call.
cat > "${work}/stubs/docker" <<'EOF'
#!/bin/sh
echo "$*" >> /bundle/docker.calls
case "$*" in
  "volume inspect "*) exit 1 ;;
  "compose up --help") echo "      --wait-timeout int" ;;
esac
exit 0
EOF
cat > "${work}/stubs/openssl" <<EOF
#!/bin/sh
case "\$*" in
  "rand -hex 24") echo ${STUB_PASSWORD} ;;
  "rand -base64 32") echo ${STUB_KEY} ;;
  *) exit 1 ;;
esac
EOF
chmod 0755 "${work}/stubs/docker" "${work}/stubs/openssl"

# Runs quickstart.sh in a fresh copy of the bundle, or the copy named, under bash 3.2.
run_quickstart() {
  local dir="${work}/$1"
  shift
  if [ ! -d "${dir}" ]; then
    mkdir -p "${dir}"
    cp "${bundle}/compose.yaml" "${bundle}/quickstart.sh" "${dir}/"
  fi
  docker run --rm --network none --user "$(id -u):$(id -g)" \
    --volume "${work}/stubs:/stubs:ro,z" --volume "${dir}:/bundle:z" \
    --env PATH=/stubs:/usr/local/bin:/usr/bin:/bin "$@" \
    "${BASH32_IMAGE}" bash /bundle/quickstart.sh
}

docker run --rm --network none "${BASH32_IMAGE}" bash --version | head -n 1

# The first run: every line from the shell clean-up to the closing instructions.
run_quickstart first --env RELEASEFLOW_HTTP_PORT=9090 --env RELEASEFLOW_AI_PROVIDER=openai \
  > "${work}/first.log" 2>&1 || { cat "${work}/first.log" >&2; fail "the first run failed"; }
expect_output "${work}/first.log" "Ignoring RELEASEFLOW_AI_PROVIDER from the shell" \
  "did not say it ignored the shell"
expect_output "${work}/first.log" "http://localhost:9090/register" "did not print where to register"
grep -qxF "RELEASEFLOW_HTTP_PORT=9090" "${work}/first/.env" || fail ".env has the wrong port"
grep -qxF "RELEASEFLOW_DB_PASSWORD=${STUB_PASSWORD}" "${work}/first/.env" \
  || fail ".env does not hold the generated password"
grep -qxF "RELEASEFLOW_CREDENTIAL_MASTER_KEY=${STUB_KEY}" "${work}/first/.env" \
  || fail ".env does not hold the generated key"
[ "$(stat -c %a "${work}/first/.env")" = "600" ] || fail ".env can be read by others"
grep -qxF "compose --project-name releaseflow-quickstart --project-directory /bundle --file /bundle/compose.yaml --env-file /bundle/.env up --detach --wait --wait-timeout 300" \
  "${work}/first/docker.calls" || fail "did not start the stack as expected"
pass "bash 3.2 runs the first run to the end"

# A later run: the branch that reads .env instead of writing it.
before="$(sha256sum "${work}/first/.env")"
run_quickstart first > "${work}/again.log" 2>&1 \
  || { cat "${work}/again.log" >&2; fail "the second run failed"; }
expect_output "${work}/again.log" "Keeping the existing .env." "did not keep .env"
expect_output "${work}/again.log" "http://localhost:9090/register" "did not finish the second run"
[ "$(sha256sum "${work}/first/.env")" = "${before}" ] || fail "the second run changed .env"
pass "bash 3.2 runs a later run to the end"

# A port in an .env someone edited, quoted and saved with Windows line endings.
mkdir -p "${work}/edited"
cp "${bundle}/compose.yaml" "${bundle}/quickstart.sh" "${work}/edited/"
sed 's/^RELEASEFLOW_HTTP_PORT=.*/RELEASEFLOW_HTTP_PORT="9091"/; s/$/\r/' "${work}/first/.env" \
  > "${work}/edited/.env"
run_quickstart edited > "${work}/edited.log" 2>&1 \
  || { cat "${work}/edited.log" >&2; fail "the run with an edited .env failed"; }
expect_output "${work}/edited.log" "http://localhost:9091/register" \
  "did not read a quoted port with a Windows line ending"
pass "bash 3.2 reads a quoted port saved with Windows line endings"

# A port that is not one is refused before anything is written.
if run_quickstart bad-port --env RELEASEFLOW_HTTP_PORT=99999 > "${work}/bad-port.log" 2>&1; then
  fail "accepted port 99999"
fi
expect_output "${work}/bad-port.log" "RELEASEFLOW_HTTP_PORT is not a port: 99999" \
  "did not refuse port 99999"
[ ! -e "${work}/bad-port/.env" ] || fail "wrote .env for a port that is not one"
pass "bash 3.2 refuses a port that is not one"
