#!/usr/bin/env bash
# Detect unpublished SyncForge artifacts on Maven Central and emit Gradle publish/compile tasks.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=maven-central-artifacts-common.sh
source "${SCRIPT_DIR}/maven-central-artifacts-common.sh"

VERSION="${1:?Usage: plan-supplemental-maven-publish.sh <version> [output-dir]}"
OUTPUT_DIR="${2:-${RUNNER_TEMP:-/tmp}}"

mkdir -p "$OUTPUT_DIR"

missing_file="${OUTPUT_DIR}/supplemental-missing.txt"
publish_file="${OUTPUT_DIR}/supplemental-publish-tasks.txt"
compile_file="${OUTPUT_DIR}/supplemental-compile-tasks.txt"
plugin_publish_file="${OUTPUT_DIR}/supplemental-plugin-publish.txt"

: >"$missing_file"
: >"$publish_file"
: >"$compile_file"
echo "false" >"$plugin_publish_file"

declare -A seen_publish=()
declare -A seen_compile=()

add_publish_task() {
  local task="$1"
  if [[ -z "${seen_publish[$task]:-}" ]]; then
    seen_publish[$task]=1
    echo "$task" >>"$publish_file"
  fi
}

add_compile_task() {
  local task="$1"
  if [[ -z "${seen_compile[$task]:-}" ]]; then
    seen_compile[$task]=1
    echo "$task" >>"$compile_file"
  fi
}

for artifact in "${SYNCFORGE_REQUIRED_ARTIFACTS[@]}"; do
  if maven_central_artifact_present "$artifact" "$VERSION"; then
    echo "OK  ${artifact}:${VERSION}"
    continue
  fi

  echo "MISS ${artifact}:${VERSION}"
  echo "$artifact" >>"$missing_file"

  case "$artifact" in
    syncforge)
      add_publish_task ":syncforge:publishKotlinMultiplatformPublicationToMavenCentralRepository"
      add_compile_task ":syncforge:compileKotlinMetadata"
      ;;
    syncforge-android)
      add_publish_task ":syncforge:publishAndroidReleasePublicationToMavenCentralRepository"
      add_compile_task ":syncforge:assembleRelease"
      ;;
    syncforge-jvm)
      add_publish_task ":syncforge:publishJvmPublicationToMavenCentralRepository"
      add_compile_task ":syncforge:compileKotlinJvm"
      ;;
    syncforge-annotations)
      add_publish_task ":syncforge-annotations:publishKotlinMultiplatformPublicationToMavenCentralRepository"
      add_compile_task ":syncforge-annotations:compileKotlinMetadata"
      ;;
    syncforge-persistence)
      add_publish_task ":syncforge-persistence:publishKotlinMultiplatformPublicationToMavenCentralRepository"
      add_compile_task ":syncforge-persistence:compileKotlinMetadata"
      ;;
    syncforge-android-deps)
      add_publish_task ":syncforge-android-deps:publishReleasePublicationToMavenCentralRepository"
      add_compile_task ":syncforge-android-deps:assembleRelease"
      ;;
    syncforge-bom)
      add_publish_task ":syncforge-bom:publishMavenPublicationToMavenCentralRepository"
      add_compile_task ":syncforge-bom:generatePomFileForMavenPublication"
      ;;
    syncforge-ksp)
      add_publish_task ":syncforge-ksp:publishMavenPublicationToMavenCentralRepository"
      add_compile_task ":syncforge-ksp:compileKotlin"
      ;;
    syncforge-gradle-plugin)
      echo "true" >"$plugin_publish_file"
      add_compile_task "syncforge-gradle-plugin:compileKotlin"
      ;;
    *)
      echo "::error::No supplemental publish mapping for artifact: ${artifact}"
      exit 1
      ;;
  esac
done

if [[ ! -s "$missing_file" ]]; then
  echo "All required artifacts are already on Maven Central for ${VERSION}."
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "has_work=false"
      echo "missing_artifacts="
    } >>"$GITHUB_OUTPUT"
  fi
  exit 0
fi

missing_artifacts="$(tr '\n' ' ' <"$missing_file" | sed 's/ $//')"
echo "Supplemental publish plan for ${VERSION}: ${missing_artifacts}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "has_work=true"
    echo "missing_artifacts=${missing_artifacts}"
    echo "publish_tasks_file=${publish_file}"
    echo "compile_tasks_file=${compile_file}"
    echo "plugin_publish_file=${plugin_publish_file}"
  } >>"$GITHUB_OUTPUT"
fi