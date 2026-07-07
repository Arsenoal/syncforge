#!/usr/bin/env bash
# Shared Maven Central artifact list and POM probe for SyncForge release scripts.
set -euo pipefail

MAVEN_CENTRAL_BASE="${MAVEN_CENTRAL_BASE:-https://repo1.maven.org/maven2/studio/syncforge}"

# Keep in sync with build.gradle.kts mavenCentralRequiredArtifacts and verify-maven-central-artifacts.sh
SYNCFORGE_REQUIRED_ARTIFACTS=(
  syncforge-bom
  syncforge-ksp
  syncforge
  syncforge-android
  syncforge-jvm
  syncforge-annotations
  syncforge-persistence
  syncforge-android-deps
  syncforge-network-ktor
  syncforge-store-room
  syncforge-store-inmemory
  syncforge-integration-koin
  syncforge-integration-hilt
  syncforge-gradle-plugin
)

maven_central_pom_url() {
  local artifact="$1"
  local version="$2"
  echo "${MAVEN_CENTRAL_BASE}/${artifact}/${version}/${artifact}-${version}.pom"
}

maven_central_pom_status() {
  local artifact="$1"
  local version="$2"
  curl -sf -o /dev/null -w '%{http_code}' "$(maven_central_pom_url "$artifact" "$version")" || true
}

maven_central_artifact_present() {
  local artifact="$1"
  local version="$2"
  [[ "$(maven_central_pom_status "$artifact" "$version")" == "200" ]]
}