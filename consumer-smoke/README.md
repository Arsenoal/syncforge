# Consumer smoke tests

Minimal Gradle projects that depend **only** on published Maven coordinates (no `project(":syncforge")`, no `includeBuild`).

## Android minimal

Verifies the documented consumer setup:

- `id("studio.syncforge.android")` from Maven Central (or `mavenLocal()` when testing unpublished builds)
- `implementation(platform("studio.syncforge:syncforge-bom:…"))`
- `implementation("studio.syncforge:syncforge")`
- KSP handler codegen via the plugin (no manual `ksp("syncforge-ksp")`)

Version pins: `android-minimal/gradle/libs.versions.toml` and `gradle.properties` (`syncforge.version`).

### Run locally (mavenLocal — matches `verifyReleaseSignOff`)

From the repo root:

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

### Run locally (Maven Central only)

Uses the pinned version in `libs.versions.toml` — no `publishAllToMavenLocal`:

```bash
./gradlew verifyConsumerSmokeMavenCentral
```

Equivalent manual command:

```bash
./gradlew -p consumer-smoke/android-minimal :app:compileDebugKotlin \
  -Psyncforge.consumerSmoke.useMavenLocal=false
```

### CI

On every push to `main`, the **Consumer smoke (Maven Central)** job runs `verifyConsumerSmokeMavenCentral`.
It fails if the pinned version is not yet on [Maven Central](https://repo1.maven.org/maven2/studio/syncforge/).

After tagging a new release, bump the consumer-smoke version pins **before** or with the release commit so CI
validates the coordinates consumers will use.