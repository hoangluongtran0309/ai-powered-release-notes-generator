#!/usr/bin/env bash
set -euo pipefail

readonly SHELLCHECK_VERSION="0.11.0"
readonly SHELLCHECK_ARCHIVE="shellcheck-v${SHELLCHECK_VERSION}.linux.x86_64.tar.xz"
readonly SHELLCHECK_SHA256="8c3be12b05d5c177a04c29e3c78ce89ac86f1595681cab149b65b97c4e227198"

install_dir="${1:?usage: install-shellcheck.sh <install-directory>}"
tool_tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tool_tmp_dir}"' EXIT

mkdir -p "${install_dir}"
curl --fail --location --silent --show-error \
  "https://github.com/koalaman/shellcheck/releases/download/v${SHELLCHECK_VERSION}/${SHELLCHECK_ARCHIVE}" \
  --output "${tool_tmp_dir}/${SHELLCHECK_ARCHIVE}"
printf '%s  %s\n' "${SHELLCHECK_SHA256}" "${tool_tmp_dir}/${SHELLCHECK_ARCHIVE}" | sha256sum --check --strict -
tar -xJf "${tool_tmp_dir}/${SHELLCHECK_ARCHIVE}" -C "${install_dir}" --strip-components=1 \
  "shellcheck-v${SHELLCHECK_VERSION}/shellcheck"
chmod 0755 "${install_dir}/shellcheck"
"${install_dir}/shellcheck" --version
