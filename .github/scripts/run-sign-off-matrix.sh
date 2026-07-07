#!/usr/bin/env bash
# Run automated 1.0 sign-off checks (local / Linux CI parity).
# E2E (androidE2e, iosE2e) requires emulator + macOS — verified in GitHub Actions CI.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "=== SyncForge 1.0 sign-off matrix (automated) ==="
echo "Baseline: $(grep '^syncforge.version=' gradle.properties | cut -d= -f2)"
echo

./gradlew verifySignOffMatrix --no-daemon

echo
echo "=== Automated checks passed ==="
echo "CI still required for: androidE2e (emulator), iosE2e (macOS Simulator)"
echo "Full checklist: docs/ROADMAP_1_0_TO_2_0.md § 1.0.0 sign-off"
echo "Tag gate: publish 1.0.0 to Maven Central + re-run verifyConsumerSmokeMavenCentral pins"