#!/usr/bin/env bash
# Records docs/images/syncforge-demo.{mp4,gif} via in-app auto_demo (no adb input tap).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGES_DIR="$ROOT/docs/images"
RAW_MP4="$IMAGES_DIR/syncforge-demo-raw.mp4"
OUT_MP4="$IMAGES_DIR/syncforge-demo.mp4"
OUT_GIF="$IMAGES_DIR/syncforge-demo.gif"
MOCK_PORT="${PORT:-8080}"
MOCK_LOG="$(mktemp /tmp/syncforge-mock-server.XXXXXX.log)"
MOCK_PID=""

cleanup() {
  if [[ -n "$MOCK_PID" ]] && kill -0 "$MOCK_PID" 2>/dev/null; then
    kill "$MOCK_PID" 2>/dev/null || true
    wait "$MOCK_PID" 2>/dev/null || true
  fi
  rm -f "$MOCK_LOG"
}
trap cleanup EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

require_cmd adb
require_cmd ffmpeg

if ! adb devices | grep -q "device$"; then
  echo "No Android device/emulator connected." >&2
  exit 1
fi

echo "Building mock-server + sample debug APK…"
./gradlew :mock-server:installDist :sample:installDebug --no-daemon -q

MOCK_BIN="$ROOT/mock-server/build/install/mock-server/bin/mock-server"
[[ -x "$MOCK_BIN" ]] || { echo "Mock server binary missing at $MOCK_BIN" >&2; exit 1; }

echo "Starting mock-server on :$MOCK_PORT…"
"$MOCK_BIN" >"$MOCK_LOG" 2>&1 &
MOCK_PID=$!

for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:$MOCK_PORT/health" >/dev/null; then
    echo "Mock server healthy."
    break
  fi
  sleep 1
done
curl -sf "http://127.0.0.1:$MOCK_PORT/health" >/dev/null || {
  echo "Mock server failed to start. Log:" >&2
  tail -30 "$MOCK_LOG" >&2
  exit 1
}

curl -sf -X POST "http://127.0.0.1:$MOCK_PORT/dev/reset" >/dev/null

echo "Preparing emulator…"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell pm disable-user --user 0 com.google.android.calendar >/dev/null 2>&1 || true
adb shell pm clear dev.syncforge.sample >/dev/null

echo "Launching auto-demo…"
adb shell am start -n dev.syncforge.sample/.MainActivity --ez auto_demo true
sleep 2

echo "Recording screen (~38s)…"
adb shell screenrecord --size 720x1520 --time-limit 40 /sdcard/syncforge-demo.mp4 &
RECORD_PID=$!
sleep 36
kill "$RECORD_PID" 2>/dev/null || true
wait "$RECORD_PID" 2>/dev/null || true
sleep 1

adb pull /sdcard/syncforge-demo.mp4 "$RAW_MP4"
adb shell rm -f /sdcard/syncforge-demo.mp4
adb shell pm enable com.google.android.calendar >/dev/null 2>&1 || true

echo "Encoding MP4…"
ffmpeg -y -i "$RAW_MP4" \
  -vf "scale=480:-2:flags=lanczos" -c:v libx264 -pix_fmt yuv420p -crf 23 \
  -preset medium -movflags +faststart -an "$OUT_MP4"

echo "Encoding GIF…"
ffmpeg -y -i "$OUT_MP4" \
  -vf "fps=10,scale=360:-2:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" \
  -loop 0 "$OUT_GIF"

ls -lh "$OUT_MP4" "$OUT_GIF"
echo "Done: $OUT_GIF"