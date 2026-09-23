#!/usr/bin/env bash
set -euo pipefail

readonly TRIVY_VERSION="0.74.0"
readonly TRIVY_ARCHIVE="trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz"
readonly TRIVY_SHA256="2ae6fe3ee734b7fdf11335663e18c75ea12dccc76062f09f164a3b0f8be4371a"

install_dir="${1:?usage: install-trivy.sh <install-directory>}"
tool_tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tool_tmp_dir}"' EXIT

mkdir -p "${install_dir}"
curl --fail --location --silent --show-error \
  "https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/${TRIVY_ARCHIVE}" \
  --output "${tool_tmp_dir}/${TRIVY_ARCHIVE}"
printf '%s  %s\n' "${TRIVY_SHA256}" "${tool_tmp_dir}/${TRIVY_ARCHIVE}" | sha256sum --check --strict -
tar -xzf "${tool_tmp_dir}/${TRIVY_ARCHIVE}" -C "${install_dir}" trivy
chmod 0755 "${install_dir}/trivy"
"${install_dir}/trivy" --version
