# Consumer smoke tests

Minimal Gradle projects that depend **only** on published Maven coordinates (no `project(":syncforge")`, no `includeBuild`).

## Android minimal

Verifies the documented consumer setup:

- `syncforge-catalog` imported in `settings.gradle.kts` (Maven Central or `mavenLocal()`)
- `alias(syncforge.plugins.syncforge.android)` — plugin version from catalog
- `implementation(syncforge.core)` — version catalog pins all coordinates
- KSP handler codegen via the plugin (no manual `ksp("syncforge-ksp")`)

### Version pins (two contexts)

| Task | Version source | When to bump |
|------|----------------|--------------|
| `verifyConsumerSmokeMavenCentral` | `android-minimal/gradle.properties` (`syncforge.version` → catalog coordinate) | **After** a **2.0.0+** release is live on [Maven Central](https://repo1.maven.org/maven2/studio/syncforge/) (1.x tags do not publish) |
| `verifyConsumerSmoke` (mavenLocal) | Root `gradle.properties` via `-Psyncforge.version=…` | Automatic — tracks the library version under development |

Do **not** bump the consumer-smoke Maven Central pins when tagging; wait until Central sync completes, then update pins so CI validates coordinates consumers actually resolve.

### Run locally (mavenLocal — matches `verifyReleaseSignOff`)

From the repo root:

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

Uses the repo's `syncforge.version` (e.g. `1.1.0`) from `mavenLocal()` after publish.

### Run locally (Maven Central only)

Uses the pinned **published** version in `gradle.properties` — no `publishAllToMavenLocal`:

```bash
./gradlew verifyConsumerSmokeMavenCentral
```

Equivalent manual command:

```bash
./gradlew -p consumer-smoke/android-minimal :app:compileDebugKotlin \
  -Psyncforge.consumerSmoke.useMavenLocal=false
```

### CI

`verifyConsumerSmokeMavenCentral` is run manually after a **2.0.0+** Central publish
(Actions → Verify Maven Central Release), or locally before bumping consumer-smoke pins.

After a 2.0+ release appears on Central, bump `consumer-smoke/android-minimal/gradle.properties`, then push.