# SyncForge demo GIF

Assets for the README **See it in action** section.

| File | Purpose |
|------|---------|
| `syncforge-demo.mp4` | Main README demo video (~40s, embedded in README) |

---

## Quick record (Android emulator)

### 1. Setup

```bash
# Terminal 1 — mock server
./gradlew :mock-server:run

# Terminal 2 — install sample (API 29+ emulator)
./gradlew :sample:installDebug
adb shell am start -n dev.syncforge.sample/.MainActivity
```

Emulator must reach the server at `http://10.0.2.2:8080` (default in `:sample`).

### 2. Shot list (~60 seconds)

Record the **Tasks** tab in one continuous take. The sample app shows an **Under the hood (demo)** panel (debug builds) that narrates Room, outbox, and sync steps — keep it in frame.

| Time | Action | Show on screen |
|------|--------|----------------|
| **0–15s** | Enable **airplane mode** → add task *"Buy milk"* | Highlight field: *Room UPDATE + outbox enqueue*; task appears; *Offline* |
| **15–30s** | Disable airplane mode → tap **Sync** | *Push to server* / *Pull from server* log lines; row *Synced* |
| **30–45s** | Tap **Clear local DB** → tap **Sync** | Empty list → *PULL remote data from mock-server* → tasks reappear in Room |
| **45–55s** | **Server edit** → local edit → **Sync** | Conflict chip → **Resolve** |
| **55–60s** | (Optional) Tap **SF** debug button | Outbox / events / conflicts panel |

Use the in-app **Server edit** button (calls `POST /dev/simulate-edit`) — no curl needed.

### 3. Capture video

**Option A — Android Studio (easiest)**

1. Run the emulator with the sample app.
2. **View → Tool Windows → Logcat** (or emulator toolbar) → **Screen record** (camera icon).
3. Perform the shot list → stop recording → save as `syncforge-demo.mp4`.

**Option B — `adb screenrecord`**

```bash
adb shell screenrecord /sdcard/syncforge-demo.mp4
# … perform the demo (max 180s) …
# Ctrl+C to stop early
adb pull /sdcard/syncforge-demo.mp4 /tmp/syncforge-demo.mp4
```

### 4. Compress for README

Requires [ffmpeg](https://ffmpeg.org/).

```bash
# Lock portrait, launch app, wait until UI is visible — then start screenrecord
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell am start -n dev.syncforge.sample/.MainActivity
sleep 5
adb shell screenrecord --size 720x1520 --time-limit 35 /sdcard/syncforge-demo.mp4 &
# … run demo steps …
pkill -f "adb shell screenrecord"
adb pull /sdcard/syncforge-demo.mp4 docs/images/syncforge-demo-raw.mp4

ffmpeg -y -i docs/images/syncforge-demo-raw.mp4 \
  -vf "scale=480:-2:flags=lanczos" -c:v libx264 -pix_fmt yuv420p -crf 23 \
  -preset medium -movflags +faststart -an docs/images/syncforge-demo.mp4
```

### 5. Preview locally

```bash
ls -lh docs/images/syncforge-demo.mp4
xdg-open docs/images/syncforge-demo.mp4   # Linux
open docs/images/syncforge-demo.mp4       # macOS
```

### 6. Commit

```bash
git add docs/images/syncforge-demo.mp4 README.md
git commit -m "docs: add README demo video"
git push
```

[README.md](../../README.md) embeds the video with an HTML `<video>` tag (autoplay, loop, muted).

---

## Tips

- Use a **clean emulator** (Pixel 6, light theme) and hide the on-screen keyboard when possible.
- Keep the status banner and conflict chip in frame during the conflict scene.
- **Notes / Tags** tabs are optional B-roll — Tasks alone tells the story.
- For iOS, record the Simulator separately and save as `syncforge-demo-ios.gif` (link from README later if desired).

---

## Related

- [README demo section](../../README.md#see-it-in-action)
- [Getting Started — conflict demo](../GETTING_STARTED.md#try-the-conflict-demo-optional)
- [Launch playbook](../SyncForge-GitHub-Launch-Playbook.docx) §9 Launch content kit