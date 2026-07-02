#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PORT="${PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/health"

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

echo "Running connected Android tests..."
./gradlew :sample:connectedDebugAndroidTest --no-daemon "$@"