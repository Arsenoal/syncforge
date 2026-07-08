# Desktop setup guide

Run SyncForge on JVM desktop (Linux, macOS, Windows) via `SyncForge.desktop { }` (**stable** since 1.3 — no module-wide `@OptIn` required for the desktop DSL).

**Requirements:** JDK 17+, Kotlin 2.1+

---

## Quick start

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.desktop
import dev.syncforge.persistence.createDefaultSyncForgePersistence

val syncManager = SyncForge.desktop {
    baseUrl("http://localhost:8080")
    registry(handlers)
}
```

### Defaults applied automatically

| Component | Implementation |
|-----------|----------------|
| Outbox + conflicts | SQLDelight (JDBC SQLite in temp dir via `createDefaultSyncForgePersistence()`) |
| Pull cursor | `FileSyncCursorStore` in `~/.syncforge/syncforge_cursor.properties` |
| Network | `AlwaysOnlineNetworkMonitor` |
| Transport | `KtorSyncTransport` (OkHttp engine) |
| Background sync | `NoOpSyncWorkScheduler` |
| Token storage (built-in `auth { }`) | `InMemoryTokenStore` — tokens lost when the process exits |

For production desktop apps with built-in auth, prefer `auth(SyncAuthProvider)` backed by your own
secure storage (OS keychain bindings, encrypted file, etc.). See [AUTH_API.md](AUTH_API.md).

---

## Local development with mock server

```bash
./gradlew :mock-server:run
```

Use `http://localhost:8080` as `baseUrl`.

### Desktop sample (`:sample-desktop`)

Minimal JVM app proving `SyncForge.desktop { }` against `:mock-server` (push + pull):

```bash
./gradlew :mock-server:run
# another terminal:
./gradlew :sample-desktop:run --args="--smoke"

Compose conflict UI demo (1.3-05):

```bash
./gradlew :sample-desktop:runComposeConflictDemo
```

See [COMPOSE_UI.md](COMPOSE_UI.md).
```

Full CI-style check (starts mock-server automatically):

```bash
./gradlew desktopE2e
```

---

## macOS native target

For native macOS apps (not JVM), use `SyncForge.macos { }` (**stable** since 1.3) — same defaults as iOS:

```kotlin
val syncManager = SyncForge.macos {
    baseUrl("https://api.example.com")
    registry(handlers)
}
```

Build the framework on macOS:

```bash
./gradlew :syncforge:linkDebugFrameworkMacosArm64
```

---

## Gradle targets (M5)

| Target | Use case | Source set |
|--------|----------|------------|
| `jvm` | Desktop apps, CLI, integration tests | `jvmMain` |
| `macosArm64` / `macosX64` | Native macOS Xcode apps | `macosMain` (+ `iosMain` shared services) |

---

## Cursor store

Override the default file location:

```kotlin
import dev.syncforge.sync.SyncCursorStoreFactory
import java.io.File

SyncForge.desktop {
    cursorStore(SyncCursorStoreFactory.create(directory = File("/var/app/syncforge")))
}
```

Android uses DataStore Preferences for the pull cursor since 1.1. iOS (`UserDefaults`) and
desktop (`FileSyncCursorStore`) remain until a unified KMP cursor ships in a later release.

---

## Verification

```bash
./gradlew :syncforge:compileKotlinJvm
./gradlew :syncforge:jvmTest
```

Native macOS compilation requires a Mac with Xcode:

```bash
./gradlew :syncforge:compileKotlinMacosArm64
```