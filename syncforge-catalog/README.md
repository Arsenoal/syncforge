# SyncForge version catalog

Published Gradle version catalog for SyncForge consumers. Pins every library artifact
and the `studio.syncforge.android` plugin to a single `syncforge` version — alternative
to `syncforge-bom` without `platform(...)`.

## Import

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    versionCatalogs {
        create("syncforge") {
            from("studio.syncforge:syncforge-catalog:1.2.0")
        }
    }
}
```

## Use

```kotlin
// app/build.gradle.kts
plugins {
    alias(syncforge.plugins.syncforge.android)
}

dependencies {
    implementation(syncforge.core)
    implementation(syncforge.transport.core) // optional modules — same version pin
}
```

| Catalog alias | Maven coordinate |
|---------------|------------------|
| `core` | `studio.syncforge:syncforge` |
| `annotations` | `studio.syncforge:syncforge-annotations` |
| `ksp` | `studio.syncforge:syncforge-ksp` |
| `persistence` | `studio.syncforge:syncforge-persistence` |
| `android-deps` | `studio.syncforge:syncforge-android-deps` |
| `network-ktor` | `studio.syncforge:syncforge-network-ktor` |
| `transport-core` | `studio.syncforge:syncforge-transport-core` |
| `transport-supabase` | `studio.syncforge:syncforge-transport-supabase` |
| `transport-firebase` | `studio.syncforge:syncforge-transport-firebase` |
| `transport-graphql` | `studio.syncforge:syncforge-transport-graphql` |
| `store-room` | `studio.syncforge:syncforge-store-room` |
| `store-inmemory` | `studio.syncforge:syncforge-store-inmemory` |
| `integration-koin` | `studio.syncforge:syncforge-integration-koin` |
| `integration-hilt` | `studio.syncforge:syncforge-integration-hilt` |
| `integration-opentelemetry` | `studio.syncforge:syncforge-integration-opentelemetry` |

Plugin: `syncforge.plugins.syncforge.android` → `id("studio.syncforge.android")`.

See [GETTING_STARTED.md](../docs/GETTING_STARTED.md) for the minimal Android setup.