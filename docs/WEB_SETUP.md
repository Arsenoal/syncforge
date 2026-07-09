# Web / browser setup guide

Run SyncForge in the browser via `SyncForge.web { }` (**experimental** — requires
`@OptIn(ExperimentalSyncForgeApi::class)` until a future stability promotion).

**Requirements:** Kotlin 2.1+, JDK 17+ (Gradle), a browser target (`js(IR) { browser() }`)

Related: [WEB_DSL.md](WEB_DSL.md) (API reference), [WEB_SPIKE.md](WEB_SPIKE.md) (platform go/no-go),
[:sample-web](../sample-web/) (reference app).

---

## Quick start

```kotlin
import dev.syncforge.SyncForge
import dev.syncforge.web

suspend fun main() {
    val syncManager = SyncForge.web {
        baseUrl("http://localhost:8080")
        registry(myEntityRegistry)
        databaseName("my-app-syncforge")
        syncOnTabVisible() // optional: sync when tab visible + online
    }
}
```

### Defaults applied automatically

| Component | Implementation |
|-----------|----------------|
| Outbox + conflicts | SQLDelight `web-worker-driver` (SQL.js in a dedicated worker) |
| Pull cursor | `localStorage` keyed by `databaseName` |
| Network | `BrowserNetworkMonitor` (`navigator.onLine` + `online`/`offline`) |
| Transport | Built-in `ktor-client-js` (`createWebKtorSyncTransport`) — **no** `:syncforge-network-ktor` required |
| Background sync | `NoOpSyncWorkScheduler` — use `syncOnTabVisible()` or app-driven `sync()` |

---

## Gradle consumer snippet

### Project dependencies (monorepo / composite)

Mirror [:sample-web](../sample-web/build.gradle.kts):

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets.all {
        languageSettings.optIn("dev.syncforge.api.ExperimentalSyncForgeApi")
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":syncforge"))
            implementation(project(":syncforge-persistence"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // SQLDelight web-worker + webpack (see below)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines)
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
            implementation(devNpm("@cashapp/sqldelight-sqljs-worker", "2.0.2"))
            implementation(devNpm("path-browserify", "1.0.1"))
            implementation(devNpm("crypto-browserify", "3.12.1"))
            implementation(devNpm("stream-browserify", "3.0.0"))
            implementation(devNpm("buffer", "6.0.3"))
            implementation(npm("sql.js", "1.12.0"))
        }
    }
}
```

### Maven Central

Web `js` targets are **not published** to Maven Central. The 1.6
add-on is **monorepo-only** — use a composite build, git submodule, or `publishToMavenLocal` for
experiments. Android-primary consumers do not need the `js` target.

---

## Webpack bundling (SQL.js + worker)

SQLDelight's browser driver loads SQL.js inside a **web worker**. Webpack 5 needs explicit
polyfills and a copied `sql-wasm.wasm` asset. Copy from
[:sample-web/webpack.config.d/](../sample-web/webpack.config.d/):

**`webpack.config.d/sqljs-polyfills.js`** — Node core module fallbacks + `Buffer` provide plugin.

**`webpack.config.d/sqljs-copy.js`** — copies `sql-wasm.wasm` into the production bundle.

Without these files, `jsBrowserProductionWebpack` fails with unresolved `sql.js`, `crypto`, or
`buffer` modules, or runtime WASM load errors.

After adding npm deps, refresh the lockfile once:

```bash
./gradlew kotlinUpgradeYarnLock
```

---

## CORS and dev backend

Browser `fetch` to a different origin than your app page requires **CORS** headers on the sync
API (`POST /sync/push`, `POST /sync/pull`, and any auth routes).

### Local dev — `:mock-server`

`:mock-server` installs dev CORS via `installSyncServerDevCors()` (`anyHost()` — **dev only**):

```bash
./gradlew :mock-server:run
```

Use `http://127.0.0.1:8080` (or `PORT`) as `baseUrl`. The Kotlin/JS dev server (webpack) serves
your app on a different port (e.g. `8081`) — CORS on mock-server allows cross-origin push/pull.

`:sample-web` smoke:

```bash
./gradlew :sample-web:jsBrowserDevelopmentRun   # open app; append ?smoke=1
./gradlew webE2e                                 # headless smoke (default mock-server port 18080)
```

**CI:** nightly workflow [`.github/workflows/web-e2e.yml`](../.github/workflows/web-e2e.yml)
(schedule + `workflow_dispatch`). Not gated on every PR — same pattern as multi-device Android E2E.
Requires Chrome/Chromium on the runner (`CHROME_PATH`); GitHub Actions uses `browser-actions/setup-chrome`.

### Production backends

Do **not** use `anyHost()` in production. Configure explicit allowed origins on your Ktor/Spring
server, for example:

```kotlin
install(CORS) {
    allowHost("app.example.com", schemes = listOf("https"))
    allowHost("localhost:8081", schemes = listOf("http")) // dev SPA only
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
}
```

Alternatively, serve the SPA and API from the **same origin** (reverse proxy) so CORS is not
required for sync calls.

Reference backends without dev CORS: `:backend-starter`, `:backend-starter-spring` — add CORS
yourself when testing from a browser SPA.

---

## Verify compile

```bash
./gradlew verifyWebCompile          # :syncforge + persistence js targets
./gradlew :sample-web:compileKotlinJs
./gradlew verifyReleaseSignOff      # includes :sample-web:compileKotlinJs
```

---

## Explicit limitations (1.6.x)

| Topic | Behaviour |
|-------|-----------|
| **API stability** | `SyncForge.web { }` is `@ExperimentalSyncForgeApi` — opt in module-wide or per call site |
| **Persistence** | SQL.js storage is **in-memory per tab session** inside a worker; outbox/conflicts are lost on full page reload. IndexedDB-backed persistence is future work |
| **Storage quotas** | Browser storage limits apply to `localStorage` (cursor) and worker memory; large outboxes are not tuned for web yet |
| **Background sync** | No Service Worker / periodic sync guarantee — use `syncOnTabVisible()`, `online` events, or explicit `sync()` |
| **Maven publish** | Web `js` artifacts are **not** on Maven Central — monorepo / composite / `publishToMavenLocal` only |
| **Wasm** | Full persistence on `wasmJs` blocked until SQLDelight ≥2.1 — see [WEB_SPIKE.md](WEB_SPIKE.md) |
| **Compose Web** | Conflict/debug CMP components on web deferred to **1.6-07** |

See also [BEST_PRACTICES.md → JS/Web SDK](BEST_PRACTICES.md#how-syncforge-compares-faq).

---

## Related

- [WEB_DSL.md](WEB_DSL.md) — DSL options and transport overrides
- [MODULES.md](MODULES.md) — stability table and package map
- [REST_API.md](REST_API.md) — push/pull contract your backend must implement
- [DESKTOP_SETUP.md](DESKTOP_SETUP.md) — JVM desktop sample (`:sample-desktop`)