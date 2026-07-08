# SyncForge.web { } — browser DSL (1.6-02)

Kotlin/JS browser entry point mirroring [SyncForge.desktop](sample-desktop) and [SyncForge.ios](ios-sample).

## Gradle (app module)

```kotlin
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        jsMain.dependencies {
            implementation("studio.syncforge:syncforge:…")
            implementation("studio.syncforge:syncforge-persistence:…")
            // optional: implementation("studio.syncforge:syncforge-network-ktor:…")
        }
    }
}
```

`:syncforge-persistence` on JS needs the SQL.js worker npm packages (copy from [`:web-spike`](../web-spike/build.gradle.kts)):

```kotlin
jsMain.dependencies {
    implementation(devNpm("copy-webpack-plugin", "9.1.0"))
    implementation(devNpm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
}
```

## Bootstrap

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.web

suspend fun main() {
    val syncManager = SyncForge.web {
        baseUrl("http://localhost:8080")
        registry(myEntityRegistry)
        databaseName("my-app-syncforge")
        syncOnTabVisible() // optional: sync when tab becomes visible + online
    }
}
```

## Defaults

| Concern | 1.6-02 default | Notes |
|---------|----------------|-------|
| **Persistence** | SQLDelight `web-worker-driver` | In-memory SQL.js per tab session; schema via `createWebSyncForgePersistence` |
| **Cursor** | `localStorage` keyed by `databaseName` | `SyncCursorStoreFactory.createForDatabase(name)` |
| **Transport** | [createWebKtorSyncTransport](WEB_DSL.md) (`ktor-client-js` / fetch) | Built into `:syncforge` js; optional `:syncforge-network-ktor` for `KtorSyncTransport` |
| **Network** | `BrowserNetworkMonitor` | `navigator.onLine` + `online`/`offline` → reconnect push |
| **Background sync** | None (by design) | Use `syncOnTabVisible()` or app-driven `sync()`; no WorkManager |

## Limitations (1.6.x)

- **Storage:** Outbox/conflicts live in SQL.js **in-memory** inside a worker — data is lost on full page reload. IndexedDB-backed persistence is future work.
- **CORS:** Browser `fetch` to `:mock-server` requires CORS headers or a dev proxy — see **1.6-05** `WEB_SETUP.md`.
- **BOM:** Web artifacts are experimental; not required for Android-primary consumers.
- **Wasm:** Full persistence on `wasmJs` blocked until SQLDelight ≥2.1 — see [WEB_SPIKE.md](WEB_SPIKE.md).

## Verify compile

```bash
./gradlew verifyWebCompile
```

Runtime browser smoke (`:sample-web`, Playwright) lands in **1.6-04** / **1.6-06**.

## Explicit transport (optional)

```kotlin
import dev.syncforge.network.createWebKtorSyncTransport

val transport = createWebKtorSyncTransport(
    baseUrl = "http://localhost:8080",
    auth = SyncAuthProvider.bearer { myToken },
)
```