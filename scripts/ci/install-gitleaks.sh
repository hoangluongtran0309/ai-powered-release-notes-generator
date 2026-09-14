#!/usr/bin/env bash
set -euo pipefail

readonly GITLEAKS_VERSION="8.30.1"
readonly GITLEAKS_ARCHIVE="gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz"
readonly GITLEAKS_SHA256="551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb"

install_dir="${1:?usage: install-gitleaks.sh <install-directory>}"
tool_tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tool_tmp_dir}"' EXIT

mkdir -p "${install_dir}"
curl --fail --location --silent --show-error \
  "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/${GITLEAKS_ARCHIVE}" \
  --output "${tool_tmp_dir}/${GITLEAKS_ARCHIVE}"
printf '%s  %s\n' "${GITLEAKS_SHA256}" "${tool_tmp_dir}/${GITLEAKS_ARCHIVE}" | sha256sum --check --strict -
tar -xzf "${tool_tmp_dir}/${GITLEAKS_ARCHIVE}" -C "${install_dir}" gitleaks
chmod 0755 "${install_dir}/gitleaks"
"${install_dir}/gitleaks" version
