#!/usr/bin/env bash
set -euo pipefail

TARGET_DIR="$(pwd)"
UPSTREAM_REPO="https://github.com/VoltAgent/awesome-codex-subagents.git"
UPSTREAM_REF="main"
DRY_RUN=false

usage() {
  echo "Usage: bash scripts/update-codex-subagents.sh [/path/to/target-project] [--ref <branch-or-tag>] [--repo <git-url>] [--dry-run]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --ref)
      if [[ -z "${2:-}" ]]; then
        echo "[ERROR] --ref requires a value"
        exit 1
      fi
      UPSTREAM_REF="$2"
      shift 2
      ;;
    --repo)
      if [[ -z "${2:-}" ]]; then
        echo "[ERROR] --repo requires a value"
        exit 1
      fi
      UPSTREAM_REPO="$2"
      shift 2
      ;;
    -*)
      usage
      exit 1
      ;;
    *)
      if [[ "${TARGET_DIR}" != "$(pwd)" ]]; then
        usage
        exit 1
      fi
      TARGET_DIR="$1"
      shift
      ;;
  esac
done

if [[ ! -d "${TARGET_DIR}" ]]; then
  echo "[ERROR] target directory does not exist: ${TARGET_DIR}"
  exit 1
fi

TARGET_AGENTS_DIR="${TARGET_DIR}/.codex/agents"

if [[ ! -d "${TARGET_AGENTS_DIR}" ]]; then
  echo "[ERROR] target project does not contain .codex/agents: ${TARGET_AGENTS_DIR}"
  exit 1
fi

TMP_DIR="$(mktemp -d)"
BACKUP_DIR="${TARGET_DIR}/.policy-backup-$(date +%Y%m%d-%H%M%S)"
BACKUP_CREATED=false
UPDATED_COUNT=0
SKIPPED_COUNT=0
UNCHANGED_COUNT=0

cleanup() {
  rm -rf "${TMP_DIR}"
}

trap cleanup EXIT

readarray_compat() {
  local __var_name="$1"
  local __line

  eval "${__var_name}=()"

  while IFS= read -r __line; do
    eval "${__var_name}+=(\"\${__line}\")"
  done
}

echo "[INFO] cloning ${UPSTREAM_REPO} (${UPSTREAM_REF})"
git clone --depth 1 --branch "${UPSTREAM_REF}" "${UPSTREAM_REPO}" "${TMP_DIR}/upstream"

readarray_compat LOCAL_AGENTS < <(find "${TARGET_AGENTS_DIR}" -maxdepth 1 -type f -name '*.toml' | sort)

if [[ ${#LOCAL_AGENTS[@]} -eq 0 ]]; then
  echo "[INFO] no installed subagent files found in ${TARGET_AGENTS_DIR}"
  exit 0
fi

echo "[INFO] scanning active top-level agents only; _archive is ignored"

for local_file in "${LOCAL_AGENTS[@]}"; do
  base_name="$(basename "${local_file}")"

  readarray_compat UPSTREAM_MATCHES < <(find "${TMP_DIR}/upstream" -type f -name "${base_name}" -not -path '*/.git/*' | sort)

  if [[ ${#UPSTREAM_MATCHES[@]} -eq 0 ]]; then
    echo "[SKIP] no upstream match for ${base_name}"
    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
    continue
  fi

  if [[ ${#UPSTREAM_MATCHES[@]} -gt 1 ]]; then
    echo "[SKIP] multiple upstream matches for ${base_name}"
    printf '  - %s\n' "${UPSTREAM_MATCHES[@]}"
    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
    continue
  fi

  upstream_file="${UPSTREAM_MATCHES[0]}"
  upstream_rel="${upstream_file#${TMP_DIR}/upstream/}"

  if cmp -s "${local_file}" "${upstream_file}"; then
    echo "[OK] already up to date: ${local_file}"
    UNCHANGED_COUNT=$((UNCHANGED_COUNT + 1))
    continue
  fi

  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "[DRY-RUN] would update ${local_file} from ${upstream_rel}"
    UPDATED_COUNT=$((UPDATED_COUNT + 1))
    continue
  fi

  if [[ "${BACKUP_CREATED}" == "false" ]]; then
    mkdir -p "${BACKUP_DIR}"
    BACKUP_CREATED=true
  fi

  mkdir -p "$(dirname "${BACKUP_DIR}/.codex/agents/${base_name}")"
  cp "${local_file}" "${BACKUP_DIR}/.codex/agents/${base_name}"
  cp "${upstream_file}" "${local_file}"
  echo "[UPDATE] ${local_file} <- ${upstream_rel}"
  UPDATED_COUNT=$((UPDATED_COUNT + 1))
done

echo
echo "Summary:"
echo "  updated: ${UPDATED_COUNT}"
echo "  unchanged: ${UNCHANGED_COUNT}"
echo "  skipped: ${SKIPPED_COUNT}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "Dry run only. No files were changed."
elif [[ "${BACKUP_CREATED}" == "true" ]]; then
  echo "Backup saved to: ${BACKUP_DIR}"
else
  echo "No file changes were required."
fi
