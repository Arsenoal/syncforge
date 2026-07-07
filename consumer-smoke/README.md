# Consumer smoke tests

Minimal Gradle projects that depend **only** on published Maven coordinates (no `project(":syncforge")`, no `includeBuild`).

## Android minimal

Verifies the documented consumer setup:

- `id("studio.syncforge.android")` from Maven Central (or `mavenLocal()` when testing unpublished builds)
- `implementation(platform("studio.syncforge:syncforge-bom:…"))`
- `implementation("studio.syncforge:syncforge")`
- KSP handler codegen via the plugin (no manual `ksp("syncforge-ksp")`)

### Version pins (two contexts)

| Task | Version source | When to bump |
|------|----------------|--------------|
| `verifyConsumerSmokeMavenCentral` / CI | `android-minimal/gradle.properties` + `libs.versions.toml` | **After** the release is live on [Maven Central](https://repo1.maven.org/maven2/studio/syncforge/) |
| `verifyConsumerSmoke` (mavenLocal) | Root `gradle.properties` via `-Psyncforge.version=…` | Automatic — tracks the library version under development |

Do **not** bump the consumer-smoke Maven Central pins when tagging; wait until Central sync completes, then update pins so CI validates coordinates consumers actually resolve.

### Run locally (mavenLocal — matches `verifyReleaseSignOff`)

From the repo root:

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

Uses the repo's `syncforge.version` (e.g. `1.1.0`) from `mavenLocal()` after publish.

### Run locally (Maven Central only)

Uses the pinned **published** version in `gradle.properties` / `libs.versions.toml` — no `publishAllToMavenLocal`:

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
It fails if the pinned version is not yet on Maven Central.

After a release appears on Central, bump `consumer-smoke/android-minimal/gradle.properties` and `gradle/libs.versions.toml`, then push.