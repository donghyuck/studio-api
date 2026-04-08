#!/usr/bin/env bash
set -euo pipefail

LOCAL_NEXUS_RELEASES_URL="http://localhost:8081/repository/maven-releases/"
LOCAL_NEXUS_SNAPSHOTS_URL="http://localhost:8081/repository/maven-snapshots/"
LOCAL_NEXUS_BASE_URL="${NEXUS_URL:-http://localhost:8081}"
DELETE_EXISTING=false
MODULE_PATH=""

usage() {
  cat <<'USAGE'
Usage: scripts/publish-local-nexus.sh [options] [gradle-task-or-args...]

Publishes to the local Nexus repositories without editing gradle.properties.

Options:
  --delete-existing       Delete existing local Nexus components before publish.
  --module <gradle-path>  Module to delete/publish, for example :studio-platform-user.
                          If omitted with --delete-existing, all included modules are checked.
  -h, --help              Show this help message.

Required environment variables:
  NEXUS_USERNAME
  NEXUS_PASSWORD

Optional environment variables:
  NEXUS_URL               Local Nexus base URL. Default: http://localhost:8081

Examples:
  scripts/publish-local-nexus.sh
  scripts/publish-local-nexus.sh :studio-platform-user:publish
  scripts/publish-local-nexus.sh --delete-existing
  scripts/publish-local-nexus.sh --delete-existing --module :studio-platform-user
USAGE
}

read_property() {
  local key="$1"
  sed -n "s/^${key}=//p" gradle.properties | tail -n 1
}

artifact_id_for_module() {
  local module="$1"
  local artifact_id="${module##*:}"

  if [[ -z "${artifact_id}" ]]; then
    echo "[ERROR] invalid module path: ${module}" >&2
    exit 1
  fi

  printf '%s\n' "${artifact_id}"
}

group_id_for_module() {
  local module="$1"

  if [[ "${module}" == :starter:* ]]; then
    read_property "buildStarterGroup"
  elif [[ "${module}" == :studio-application-modules:* ]]; then
    read_property "buildModulesGroup"
  else
    read_property "buildApplicationGroup"
  fi
}

repository_for_version() {
  local version="$1"

  if [[ "${version}" == *SNAPSHOT ]]; then
    echo "maven-snapshots"
  else
    echo "maven-releases"
  fi
}

included_modules() {
  sed -n 's/^[[:space:]]*include("\([^"]*\)").*/\1/p' settings.gradle.kts
}

delete_existing_component() {
  local module="$1"
  local version
  local group_id
  local artifact_id
  local repository
  local search_url
  local component_ids

  version="$(read_property "buildApplicationVersion")"
  group_id="$(group_id_for_module "${module}")"
  artifact_id="$(artifact_id_for_module "${module}")"
  repository="$(repository_for_version "${version}")"

  if [[ -z "${version}" || -z "${group_id}" ]]; then
    echo "[ERROR] unable to resolve Gradle coordinates for ${module}" >&2
    exit 1
  fi

  search_url="${LOCAL_NEXUS_BASE_URL%/}/service/rest/v1/search?repository=${repository}&group=${group_id}&name=${artifact_id}&version=${version}"

  echo "[INFO] searching local Nexus component: ${group_id}:${artifact_id}:${version} (${repository})"
  component_ids="$(
    curl -fsS -u "${NEXUS_USERNAME}:${NEXUS_PASSWORD}" "${search_url}" \
      | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
  )"

  if [[ -z "${component_ids}" ]]; then
    echo "[INFO] no existing local Nexus component found."
    return
  fi

  while IFS= read -r component_id; do
    [[ -z "${component_id}" ]] && continue
    echo "[INFO] deleting local Nexus component: ${component_id}"
    curl -fsS -X DELETE -u "${NEXUS_USERNAME}:${NEXUS_PASSWORD}" \
      "${LOCAL_NEXUS_BASE_URL%/}/service/rest/v1/components/${component_id}"
  done <<< "${component_ids}"
}

delete_existing_components() {
  local module

  if [[ -n "${MODULE_PATH}" ]]; then
    delete_existing_component "${MODULE_PATH}"
    return
  fi

  while IFS= read -r module; do
    [[ -z "${module}" ]] && continue
    delete_existing_component "${module}"
  done < <(included_modules)
}

GRADLE_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --delete-existing)
      DELETE_EXISTING=true
      shift
      ;;
    --module)
      if [[ -z "${2:-}" ]]; then
        echo "[ERROR] --module requires a Gradle project path." >&2
        exit 1
      fi
      MODULE_PATH="$2"
      shift 2
      ;;
    *)
      GRADLE_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ -z "${NEXUS_USERNAME:-}" ]]; then
  echo "[ERROR] NEXUS_USERNAME is required for local Nexus publish." >&2
  exit 1
fi

if [[ -z "${NEXUS_PASSWORD:-}" ]]; then
  echo "[ERROR] NEXUS_PASSWORD is required for local Nexus publish." >&2
  exit 1
fi

if [[ ${#GRADLE_ARGS[@]} -eq 0 ]]; then
  if [[ -n "${MODULE_PATH}" ]]; then
    GRADLE_ARGS=("${MODULE_PATH}:publish")
  else
    GRADLE_ARGS=("publish")
  fi
fi

if [[ "${DELETE_EXISTING}" == "true" ]]; then
  delete_existing_components
fi

exec ./gradlew \
  -Pnexus.releasesUrl="${LOCAL_NEXUS_RELEASES_URL}" \
  -Pnexus.snapshotsUrl="${LOCAL_NEXUS_SNAPSHOTS_URL}" \
  -Pnexus.allowInsecure=true \
  "${GRADLE_ARGS[@]}"
