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
            implementation("studio.syncforge:syncforge-network-ktor:…") // required for REST transport
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

Register the Ktor transport adapter **once** before building the manager (same pattern as `:sample-desktop`):

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.network.ensureSyncForgeNetworkKtorLoaded
import dev.syncforge.web

suspend fun main() {
    ensureSyncForgeNetworkKtorLoaded()
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
| **Transport** | `KtorSyncTransport` (`ktor-client-js`) | Requires `:syncforge-network-ktor` on classpath |
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