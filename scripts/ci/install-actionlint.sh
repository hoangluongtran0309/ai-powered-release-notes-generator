#!/usr/bin/env bash
set -euo pipefail

readonly ACTIONLINT_VERSION="1.7.12"
readonly ACTIONLINT_ARCHIVE="actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz"
readonly ACTIONLINT_SHA256="8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"

install_dir="${1:?usage: install-actionlint.sh <install-directory>}"
tool_tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tool_tmp_dir}"' EXIT

mkdir -p "${install_dir}"
curl --fail --location --silent --show-error \
  "https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/${ACTIONLINT_ARCHIVE}" \
  --output "${tool_tmp_dir}/${ACTIONLINT_ARCHIVE}"
printf '%s  %s\n' "${ACTIONLINT_SHA256}" "${tool_tmp_dir}/${ACTIONLINT_ARCHIVE}" | sha256sum --check --strict -
tar -xzf "${tool_tmp_dir}/${ACTIONLINT_ARCHIVE}" -C "${install_dir}" actionlint
chmod 0755 "${install_dir}/actionlint"
"${install_dir}/actionlint" -version
