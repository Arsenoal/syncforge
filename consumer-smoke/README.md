# Consumer smoke tests

Minimal Gradle projects that depend **only** on published Maven coordinates (no `project(":syncforge")`, no `includeBuild`).

## Android minimal

Verifies the documented consumer setup:

- `id("studio.syncforge.android")` from `mavenLocal()` / Maven Central
- `implementation(platform("studio.syncforge:syncforge-bom:…"))`
- `implementation("studio.syncforge:syncforge")`
- KSP handler codegen via the plugin (no manual `ksp("syncforge-ksp")`)

### Run locally

From the repo root:

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

Or manually:

```bash
./gradlew publishAllToMavenLocal
./gradlew -p consumer-smoke/android-minimal :app:compileDebugKotlin
```

After a Maven Central RC publish, point `consumer-smoke/android-minimal/gradle.properties` at Central
(remove `mavenLocal()` from `settings.gradle.kts` if testing remote only).