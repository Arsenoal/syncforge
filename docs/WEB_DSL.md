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

`:syncforge-persistence` on JS needs SQL.js worker npm packages, webpack polyfills, and
`sql-wasm.wasm` copy — full consumer snippet in [WEB_SETUP.md](WEB_SETUP.md#gradle-consumer-snippet).
Reference: [`:sample-web`](../sample-web/build.gradle.kts) + [`webpack.config.d/`](../sample-web/webpack.config.d/).

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

See [WEB_SETUP.md → Explicit limitations](WEB_SETUP.md#explicit-limitations-16x) for the full table (storage, CORS, background sync, Maven publish scope, Wasm).

## Verify compile

```bash
./gradlew verifyWebCompile
```

## Sample app (`:sample-web`)

```bash
./gradlew :mock-server:run
./gradlew :sample-web:jsBrowserDevelopmentRun   # open app, append ?smoke=1 for push + pull
./gradlew webE2e                                 # headless smoke (default mock-server port 18080)
```

Runtime browser smoke: [`:sample-web`](../sample-web) (`?smoke=1` or `./gradlew webE2e`); nightly CI in [web-e2e.yml](../.github/workflows/web-e2e.yml) (**1.6-06**).

## Explicit transport (optional)

```kotlin
import dev.syncforge.network.createWebKtorSyncTransport

val transport = createWebKtorSyncTransport(
    baseUrl = "http://localhost:8080",
    auth = SyncAuthProvider.bearer { myToken },
)
```