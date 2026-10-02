#!/usr/bin/env bash
# Renders the quickstart bundle for one image: quickstart/compose.yaml with that image in
# place of its placeholder, and quickstart/quickstart.sh beside it. The Release workflow
# runs it with the digest it pushed, and the Container workflow with the image it built,
# so CI tests exactly what a release ships.
set -euo pipefail

usage="usage: package-quickstart.sh <image-reference> <output-directory>"
image="${1:?${usage}}"
output_dir="${2:?${usage}}"

case "${image}" in
  *[!a-z0-9./:_@-]*)
    echo "not an image reference this script will write into a Compose file: ${image}" >&2
    exit 1
    ;;
esac

repository_root="$(cd "$(dirname "$0")/../.." && pwd)"
template="${repository_root}/quickstart/compose.yaml"

mkdir -p "${output_dir}"
sed "s|\${RELEASEFLOW_IMAGE:?[^}]*}|${image}|" "${template}" > "${output_dir}/compose.yaml"

if grep -q 'RELEASEFLOW_IMAGE' "${output_dir}/compose.yaml"; then
  echo "the image placeholder is still in ${output_dir}/compose.yaml." >&2
  exit 1
fi
if [ "$(grep -cxF "    image: ${image}" "${output_dir}/compose.yaml")" != "1" ]; then
  echo "${output_dir}/compose.yaml does not name ${image} exactly once." >&2
  exit 1
fi

install -m 0755 "${repository_root}/quickstart/quickstart.sh" "${output_dir}/quickstart.sh"

# The rendered file must stand on its own, with nothing from this checkout or this shell.
env -u COMPOSE_FILE -u COMPOSE_ENV_FILES -u COMPOSE_PROJECT_NAME \
  RELEASEFLOW_DB_PASSWORD=validate RELEASEFLOW_CREDENTIAL_MASTER_KEY=validate \
  docker compose --project-directory "${output_dir}" --file "${output_dir}/compose.yaml" \
  --env-file /dev/null config --quiet

echo "Packaged the quickstart bundle for ${image} in ${output_dir}."
