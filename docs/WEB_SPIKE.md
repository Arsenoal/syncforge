# Web platform spike (1.6-00)

Go/no-go for SyncForge browser support. Spike modules **do not ship in the BOM** until [1.6-01](ROADMAP_1_0_TO_2_0.md#16x--web-add-on-optional) is green.

## Spike modules

| Module | Target | Proves |
|--------|--------|--------|
| [`:web-spike`](../web-spike/) | `js` (IR) browser | Ktor `ktor-client-js` + SQLDelight `web-worker-driver` + async schema |
| [`:web-spike-wasm`](../web-spike-wasm/) | `wasmJs` browser | Ktor core + JSON negotiation (transport only) |

Verify locally:

```bash
./gradlew verifyWebSpike    # spike modules (1.6-00)
./gradlew verifyWebCompile  # :syncforge + persistence + network-ktor js (1.6-01)
```

## Toolchain (spike)

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.10 |
| Ktor | 3.1.1 |
| SQLDelight | 2.0.2 |
| Coroutines | 1.10.2 |

## Findings

### Kotlin/JS (`js`) — **GO** (primary path for 1.6-01)

| Area | Result | Notes |
|------|--------|-------|
| **Ktor HTTP** | ✅ Compiles | `ktor-client-js` + ContentNegotiation + kotlinx.serialization |
| **SQLDelight persistence** | ✅ Compiles | `web-worker-driver` (replaces removed `sqljs-driver`); `generateAsync = true`; `@cashapp/sqldelight-sqljs-worker` npm + webpack worker URL |
| **Shared sync code** | ✅ Compiles | `:syncforge` + `:syncforge-persistence` + `:syncforge-network-ktor` `js` targets wired (1.6-01); `verifyWebCompile` |
| **Compose Web** | ⏸️ Deferred | Not in spike; evaluate in 1.6-04 (`:sample-web`) |

**`SyncForge.web { }` (1.6-02 — see [WEB_DSL.md](WEB_DSL.md)):**

- Outbox/conflicts: SQLDelight web-worker driver in a dedicated worker (browser-only).
- Cursor: `localStorage` / IndexedDB fallback documented in 1.6-02 if SQLDelight web storage is heavy.
- Background sync: `visibilitychange` + `online` events (no WorkManager).

### Kotlin/Wasm (`wasmJs`) — **CONDITIONAL** (secondary / later)

| Area | Result | Notes |
|------|--------|-------|
| **Ktor HTTP** | ✅ Compiles | `ktor-client-core` on wasmJs (fetch engine via Ktor 3) |
| **SQLDelight** | ❌ Blocked at 2.0.2 | `runtime-wasm-js` artifact starts at **SQLDelight 2.1.0**; sharing `:syncforge-persistence` generated code on wasm requires BOM bump or wasm-only fork |
| **Compose Multiplatform Web** | ⏸️ Deferred | Theoretical CMP Wasm path; not compile-tested in this spike |

**Wasm is viable for transport-only experiments** but **not** for full outbox parity on current SQLDelight pin without upgrading to ≥2.1.

### Deprecated / removed APIs

- `app.cash.sqldelight:sqljs-driver` — **not published** for 2.0.x; use `web-worker-driver` + SQL.js worker npm package.

## Decision (1.6-00)

| Decision | Choice |
|----------|--------|
| **1.6-01 primary target** | **`js` (Kotlin/JS IR)** |
| **1.6-01 secondary** | Document `wasmJs` transport path; full persistence after SQLDelight ≥2.1 evaluation |
| **BOM / publish** | No web artifacts until 1.6-01 acceptance |
| **CI** | `verifyWebSpike` + `verifyWebCompile` gate compile; browser runtime E2E deferred to 1.6-06 |

## Risks for 1.6-01+

| Risk | Mitigation |
|------|------------|
| Webpack + SQL.js worker bundling | `:sample-web` copies `sql-wasm.wasm` + Node polyfills — see `sample-web/webpack.config.d/` |
| CORS against `:mock-server` | `installSyncServerDevCors()` on `:mock-server` (1.6-04); production notes in 1.6-05 |
| SQLDelight async queries | All web DB access via `awaitAs*` extensions |
| Larger wasm bundle | Prefer `js` for first shippable add-on |
| No background sync guarantee | Document in BEST_PRACTICES + 1.6 limitations |

## Next jobs

1. ~~**1.6-01**~~ — `js` + `webMain` on `:syncforge` / `:syncforge-persistence` (experimental API) ✅
2. ~~**1.6-02**~~ — `SyncForge.web { }` DSL (persistence + cursor + transport) ✅ — [WEB_DSL.md](WEB_DSL.md)
3. ~~**1.6-03**~~ — `createKtorSyncTransport` browser factory (`createWebKtorSyncTransport` + `ktor-client-js`) ✅
4. **1.6-04** — `:sample-web` push/pull against `:mock-server`

## Related

- [REST_API.md](REST_API.md) — same push/pull contract
- [RATE_LIMITING.md](RATE_LIMITING.md) — client throttle for browser tabs
- [ROADMAP_1_0_TO_2_0.md](ROADMAP_1_0_TO_2_0.md#16x--web-add-on-optional)