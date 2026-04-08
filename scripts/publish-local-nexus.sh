#!/usr/bin/env bash
set -euo pipefail

LOCAL_NEXUS_RELEASES_URL="http://localhost:8081/repository/maven-releases/"
LOCAL_NEXUS_SNAPSHOTS_URL="http://localhost:8081/repository/maven-snapshots/"

usage() {
  cat <<'USAGE'
Usage: scripts/publish-local-nexus.sh [gradle-task-or-args...]

Publishes to the local Nexus repositories without editing gradle.properties.

Required environment variables:
  NEXUS_USERNAME
  NEXUS_PASSWORD

Examples:
  scripts/publish-local-nexus.sh
  scripts/publish-local-nexus.sh :studio-platform-user:publish
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "${NEXUS_USERNAME:-}" ]]; then
  echo "[ERROR] NEXUS_USERNAME is required for local Nexus publish." >&2
  exit 1
fi

if [[ -z "${NEXUS_PASSWORD:-}" ]]; then
  echo "[ERROR] NEXUS_PASSWORD is required for local Nexus publish." >&2
  exit 1
fi

GRADLE_ARGS=("$@")
if [[ ${#GRADLE_ARGS[@]} -eq 0 ]]; then
  GRADLE_ARGS=("publish")
fi

exec ./gradlew \
  -Pnexus.releasesUrl="${LOCAL_NEXUS_RELEASES_URL}" \
  -Pnexus.snapshotsUrl="${LOCAL_NEXUS_SNAPSHOTS_URL}" \
  -Pnexus.allowInsecure=true \
  "${GRADLE_ARGS[@]}"
