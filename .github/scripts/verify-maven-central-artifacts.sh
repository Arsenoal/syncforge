#!/usr/bin/env bash
# Verify required SyncForge artifacts are on repo1.maven.org after Central sync.
set -euo pipefail

VERSION="${1:?Usage: verify-maven-central-artifacts.sh <version>}"
BASE="https://repo1.maven.org/maven2/studio/syncforge"
RETRIES="${MAVEN_CENTRAL_VERIFY_RETRIES:-12}"
SLEEP_SEC="${MAVEN_CENTRAL_VERIFY_SLEEP_SEC:-30}"

REQUIRED=(
  syncforge-bom
  syncforge-ksp
  syncforge
  syncforge-jvm
  syncforge-annotations
  syncforge-persistence
  syncforge-android-deps
  syncforge-gradle-plugin
)

check_pom() {
  local artifact="$1"
  local url="${BASE}/${artifact}/${VERSION}/${artifact}-${VERSION}.pom"
  local status
  status="$(curl -sf -o /dev/null -w '%{http_code}' "$url" || true)"
  if [[ "$status" == "200" ]]; then
    echo "OK  ${artifact}:${VERSION}"
    return 0
  fi
  echo "MISS ${artifact}:${VERSION} (HTTP ${status:-000})"
  return 1
}

missing=()
for attempt in $(seq 1 "$RETRIES"); do
  missing=()
  for artifact in "${REQUIRED[@]}"; do
    if ! check_pom "$artifact"; then
      missing+=("$artifact")
    fi
  done
  if ((${#missing[@]} == 0)); then
    echo "All ${#REQUIRED[@]} required artifacts are on Maven Central."
    exit 0
  fi
  if ((attempt < RETRIES)); then
    echo "Waiting ${SLEEP_SEC}s for Central sync (attempt ${attempt}/${RETRIES})..."
    sleep "$SLEEP_SEC"
  fi
done

echo "::error::Missing on Maven Central: ${missing[*]}"
exit 1