#!/usr/bin/env bash
# Verify required SyncForge artifacts are on repo1.maven.org after Central sync.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=maven-central-artifacts-common.sh
source "${SCRIPT_DIR}/maven-central-artifacts-common.sh"

VERSION="${1:?Usage: verify-maven-central-artifacts.sh <version>}"
RETRIES="${MAVEN_CENTRAL_VERIFY_RETRIES:-12}"
SLEEP_SEC="${MAVEN_CENTRAL_VERIFY_SLEEP_SEC:-30}"

check_pom() {
  local artifact="$1"
  if maven_central_artifact_present "$artifact" "$VERSION"; then
    echo "OK  ${artifact}:${VERSION}"
    return 0
  fi
  echo "MISS ${artifact}:${VERSION} (HTTP $(maven_central_pom_status "$artifact" "$VERSION"))"
  return 1
}

missing=()
for attempt in $(seq 1 "$RETRIES"); do
  missing=()
  for artifact in "${SYNCFORGE_REQUIRED_ARTIFACTS[@]}"; do
    if ! check_pom "$artifact"; then
      missing+=("$artifact")
    fi
  done
  if ((${#missing[@]} == 0)); then
    echo "All ${#SYNCFORGE_REQUIRED_ARTIFACTS[@]} required artifacts are on Maven Central."
    exit 0
  fi
  if ((attempt < RETRIES)); then
    echo "Waiting ${SLEEP_SEC}s for Central sync (attempt ${attempt}/${RETRIES})..."
    sleep "$SLEEP_SEC"
  fi
done

echo "::error::Missing on Maven Central: ${missing[*]}"
exit 1