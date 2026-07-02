#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS UI tests require macOS with Xcode (Simulator + xcodebuild)." >&2
  exit 1
fi

PORT="${PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/health"
export MOCK_SERVER_HEALTH_URL="${HEALTH_URL}"

echo "Starting mock-server on port ${PORT}..."
./gradlew :mock-server:installDist --quiet
./mock-server/build/install/mock-server/bin/mock-server > /tmp/syncforge-mock-server.log 2>&1 &
MOCK_PID=$!

cleanup() {
  if kill -0 "$MOCK_PID" 2>/dev/null; then
    kill "$MOCK_PID" || true
  fi
}
trap cleanup EXIT

for _ in $(seq 1 30); do
  if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    echo "Mock server healthy at ${HEALTH_URL}"
    break
  fi
  sleep 1
done

if ! curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
  echo "Mock server failed to start. Log:"
  tail -50 /tmp/syncforge-mock-server.log || true
  exit 1
fi

KOTLIN_TARGET="IosSimulatorArm64"
if [[ "$(uname -m)" != "arm64" ]]; then
  KOTLIN_TARGET="IosX64"
fi

echo "Pre-building Kotlin frameworks (${KOTLIN_TARGET})..."
./gradlew \
  ":syncforge:linkDebugFramework${KOTLIN_TARGET}" \
  ":sample-ios-shared:linkDebugFramework${KOTLIN_TARGET}" \
  --quiet

resolve_destination() {
  if [[ -n "${IOS_SIMULATOR_DESTINATION:-}" ]]; then
    echo "${IOS_SIMULATOR_DESTINATION}"
    return
  fi
  local name
  for name in "iPhone 16" "iPhone 15" "iPhone 14"; do
    if xcrun simctl list devices available | grep -F "${name} (" >/dev/null 2>&1; then
      echo "platform=iOS Simulator,name=${name}"
      return
    fi
  done
  echo "platform=iOS Simulator,name=iPhone 16"
}

DESTINATION="$(resolve_destination)"
echo "Running XCUITest on ${DESTINATION}..."

set +e
xcodebuild test \
  -project ios-sample/SyncForgeTasks.xcodeproj \
  -scheme SyncForgeTasks \
  -destination "${DESTINATION}" \
  -only-testing:SyncForgeTasksUITests \
  CODE_SIGNING_ALLOWED=NO \
  "$@"
RESULT=$?
set -e

if [[ "${RESULT}" -ne 0 ]]; then
  echo "xcodebuild test failed (exit ${RESULT}). Recent mock-server log:"
  tail -30 /tmp/syncforge-mock-server.log || true
fi

exit "${RESULT}"