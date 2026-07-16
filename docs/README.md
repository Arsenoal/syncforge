# Documentation

**Latest Maven Central release:** `v2.0.0` — [Changelog § 2.0.0](../CHANGELOG.md#200---2026-07-09).  
**Monorepo (`main`):** `2.0.1` (Integration DX; not on Central yet) — [Changelog § 2.0.1](../CHANGELOG.md#201---2026-07-09).

Consumer install examples and the version catalog pin use **`2.0.0`** until `2.0.1` is published.

The [repository README](../README.md) is the landing page (what SyncForge is/isn't, current
state, use cases, limitations, demo, quick start). This folder holds integration guides,
recipes, and API reference.

---

## Start here

| I want to… | Read |
|------------|------|
| **See every feature + a code sample** | [FEATURES.md](FEATURES.md) |
| **Build my first app (~10 min)** | [GETTING_STARTED.md](GETTING_STARTED.md) |
| **Upgrade from `1.1.0` to `2.0.0`** | [UPGRADE_1_1_TO_2_0.md](UPGRADE_1_1_TO_2_0.md) |
| **Copy-paste one task** | [RECIPES.md](RECIPES.md) |
| **Look up a type** | [MODULES.md](MODULES.md) |

---

## Platform setup (Gradle + DSL)

Implementation notes for each target — dependencies, wiring, and platform-specific options:

| Platform | Guide | Sample |
|----------|-------|--------|
| Android | [ANDROID_SETUP.md](ANDROID_SETUP.md) | [`:sample`](../sample/) |
| iOS | [IOS_SETUP.md](IOS_SETUP.md) · [SWIFT_INTEROP.md](SWIFT_INTEROP.md) | [`:ios-sample`](../ios-sample/) |
| JVM desktop / macOS | [DESKTOP_SETUP.md](DESKTOP_SETUP.md) | [`:sample-desktop`](../sample-desktop/) |
| Browser (experimental) | [WEB_SETUP.md](WEB_SETUP.md) · [WEB_DSL.md](WEB_DSL.md) | [`:sample-web`](../sample-web/) |

---

## Topics

| Topic | Doc |
|-------|-----|
| Conflicts & strategies | [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) |
| Parent/child FKs | [HIERARCHICAL_SYNC.md](HIERARCHICAL_SYNC.md) |
| Entity design | [BEST_PRACTICES.md](BEST_PRACTICES.md) |
| Built-in auth | [AUTH_API.md](AUTH_API.md) |
| Backend push/pull contract | [REST_API.md](REST_API.md) |
| GraphQL / Supabase / Firebase / custom | [CUSTOM_TRANSPORT.md](CUSTOM_TRANSPORT.md) |
| OpenTelemetry tracing | [TRACING.md](TRACING.md) |
| Rate limiting & backoff | [RATE_LIMITING.md](RATE_LIMITING.md) |
| Audit export | [AUDIT_EXPORT.md](AUDIT_EXPORT.md) |
| Compose conflict UI | [COMPOSE_UI.md](COMPOSE_UI.md) |
| KMP migration notes | [KMP_MIGRATION.md](KMP_MIGRATION.md) |
| Upgrade `1.1.0` → `2.0.0` | [UPGRADE_1_1_TO_2_0.md](UPGRADE_1_1_TO_2_0.md) |
| Future work | [ROADMAP.md](ROADMAP.md) |

---

## Backend reference

| Module | Run |
|--------|-----|
| [`:mock-server`](../mock-server/) | `./gradlew :mock-server:run` |
| [`:backend-starter`](../backend-starter/) | `./gradlew :backend-starter:run` |
| [`:backend-starter-spring`](../backend-starter-spring/) | `./gradlew :backend-starter-spring:bootRun` |
| [`:backend-starter-graphql`](../backend-starter-graphql/) | `./gradlew :backend-starter-graphql:run` |

---

## Contributing to docs

When adding a feature:

1. [CHANGELOG.md](../CHANGELOG.md)
2. [FEATURES.md](FEATURES.md) — one sample + table row
3. Platform guide or [RECIPES.md](RECIPES.md) as appropriate
4. [MODULES.md](MODULES.md)
5. One line in [README.md](../README.md) only if it changes the landing-page story