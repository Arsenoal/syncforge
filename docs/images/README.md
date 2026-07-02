# SyncForge demo GIF

Assets for the README **See it in action** section.

| File | Purpose |
|------|---------|
| `syncforge-demo.gif` | Main README demo (target ~60s, &lt; 5 MB) |
| `syncforge-demo-placeholder.svg` | Static stand-in until the GIF is recorded |

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

### 4. Convert to GIF

Requires [ffmpeg](https://ffmpeg.org/).

```bash
# Trim, scale for README (max width 800px), optimize palette
ffmpeg -i syncforge-demo.mp4 -vf "fps=12,scale=800:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" \
  -loop 0 docs/images/syncforge-demo.gif
```

If the file is too large (&gt; 5 MB for GitHub):

```bash
ffmpeg -i syncforge-demo.mp4 -t 45 -vf "fps=10,scale=640:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse" \
  -loop 0 docs/images/syncforge-demo.gif
```

### 5. Preview locally

```bash
ls -lh docs/images/syncforge-demo.gif
xdg-open docs/images/syncforge-demo.gif   # Linux
open docs/images/syncforge-demo.gif       # macOS
```

### 6. Commit

```bash
git add docs/images/syncforge-demo.gif
git commit -m "docs: add README demo GIF"
git push
```

In [README.md](../../README.md), change the demo `img src` from `syncforge-demo-placeholder.svg` to `syncforge-demo.gif`. Push — the animation appears on the repo front page.

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