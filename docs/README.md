# Documentation folder

The **documentation index**, [Feature catalog](../README.md#feature-catalog), learning paths,
demo GIF, and quick-start guides live on the [repository README](../README.md) — that file is what
GitHub shows on the project homepage.

**Current release:** `v2.0.0` on Maven Central — [Changelog § 2.0.0](../CHANGELOG.md#200---2026-07-09).

---

## Quick navigation

| I need… | Go to |
|---------|-------|
| **Every feature + a code sample** | [README → Feature catalog](../README.md#feature-catalog) |
| **First integration (~10 min)** | [GETTING_STARTED.md](GETTING_STARTED.md) |
| **Copy-paste for one task** | [RECIPES.md](RECIPES.md) |
| **Look up a type or package** | [MODULES.md](MODULES.md) |
| **v2.0.0 release notes** | [CHANGELOG.md § 2.0.0](../CHANGELOG.md#200---2026-07-09) |

---

## Files in `docs/`

```
docs/
├── README.md                 ← You are here (folder index; main hub is ../README.md)
├── GETTING_STARTED.md        ← Zero → working offline-first app (~10 min)
├── ANDROID_SETUP.md          ← Android DSL, SQLDelight default, Room migration
├── IOS_SETUP.md              ← iOS DSL, SQLDelight defaults, Swift integration
├── DESKTOP_SETUP.md          ← JVM desktop + native macOS DSL
├── WEB_SETUP.md              ← Kotlin/JS browser DSL, webpack, CORS
├── WEB_DSL.md                ← SyncForge.web { } API reference
├── WEB_SPIKE.md              ← Web platform spike go/no-go (js vs wasm)
├── RECIPES.md                ← How-to: merge, deferToUser, transports, DI, debug
├── CUSTOM_TRANSPORT.md       ← BYO SyncTransport / SyncDeltaStore
├── TRACING.md                ← Opt-in OpenTelemetry spans
├── RATE_LIMITING.md          ← Backoff policies, 429 handling
├── AUDIT_EXPORT.md           ← Conflict audit CSV/JSON export
├── HIERARCHICAL_SYNC.md      ← Parent/child FK recipes, orphan policies
├── CONFLICT_RESOLUTION.md    ← Strategies, lifecycle, Compose UI, decision guide
├── BEST_PRACTICES.md         ← Entity design, strategy choices, performance
├── KMP_MIGRATION.md          ← Room → SQLDelight, iOS targets, expect/actual plan
├── MODULES.md                ← Package-by-package API reference
├── REST_API.md               ← Backend push/pull contract + transport adapter semantics
├── AUTH_API.md               ← Built-in register/login/refresh (Android flow + diagram)
├── COMPOSE_UI.md             ← CMP conflict UI
├── SWIFT_INTEROP.md          ← SKIE / Swift consumer patterns
├── ROADMAP.md                ← Future work
├── RELEASE.md                ← Maintainer release process
├── MAVEN_PUBLISH.md          ← Maven Central publish + verify workflow
└── images/                   ← README demo GIF (+ recording guide)
```

Historical planning and migration guides (`ROADMAP_1_0_TO_2_0.md`, `UPGRADE_1_1_TO_2_0.md`) remain
in the repo for maintainers but are not linked from the consumer README.

---

## Sample code map

| Module | Doc link | Features demonstrated |
|--------|----------|------------------------|
| `:sample` | [Feature catalog → Android](../README.md#platform-entry-points) | Multi-entity sync, `gitLike`, runtime policy, debug UI, Compose conflicts |
| `:sample-ios-shared` | [IOS_SETUP.md](IOS_SETUP.md) | `SyncForge.ios { }`, handlers without Room |
| `:sample-desktop` | [DESKTOP_SETUP.md](DESKTOP_SETUP.md) | `SyncForge.desktop { }`, in-memory `EntityStore` |
| `:sample-web` | [WEB_SETUP.md](WEB_SETUP.md) | `SyncForge.web { }`, browser push/pull |
| `:mock-server` | [REST_API.md](REST_API.md) | Contract routes + `/dev/simulate-edit` conflict demos |
| `:backend-starter-spring` | [REST_API.md](REST_API.md) | Spring Boot + JDBC store |
| `:backend-starter-graphql` | [RECIPES.md → GraphQL](RECIPES.md#graphql-sync-transport-client) | GraphQL sync schema |

---

## Contributing to docs

When adding a feature, update in this order:

1. [CHANGELOG.md](../CHANGELOG.md)
2. [README.md → Feature catalog](../README.md#feature-catalog) — one sample + table row
3. Relevant guide (Getting Started / Recipes / Conflict Resolution)
4. [MODULES.md](MODULES.md) API reference