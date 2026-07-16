# Maven Central publish checklist

Step-by-step guide for publishing SyncForge to Maven Central.

**Repository:** [github.com/Arsenoal/syncforge](https://github.com/Arsenoal/syncforge)  
**Group ID:** `studio.syncforge` (namespace verified via DNS on `syncforge.studio`)  
**Workflow:** [.github/workflows/publish-release.yml](../.github/workflows/publish-release.yml) (manual **workflow_dispatch** only — see [RELEASE.md](RELEASE.md))

### Distribution policy (1.x → 2.0)

| Version | Maven Central | iOS SPM / XCFramework | How to integrate |
|---------|---------------|----------------------|------------------|
| **1.0.x – 1.x** | **Not published** | **Not published** | Git clone, `publishAllToMavenLocal`, composite/`includeBuild`; iOS via KMP frameworks ([IOS_SETUP.md](IOS_SETUP.md)) |
| **2.0.0+** | **Published** (full KMP artifact set) | **Planned `2.0.1+`** (stub at 2.0.0) | Maven coordinates + Gradle plugin from Central; iOS via KMP frameworks until SPM ships |

**Current release on Central:** **`2.0.0`** (tag `v2.0.0`, July 2026).  
**Monorepo (`main`):** **`2.0.1`** — API graduation; not on Central until `v2.0.1` is published. Consumer install pins and `consumer-smoke` stay on **`2.0.0`** until then.

Monorepo tags `v1.2.0`–`v1.6.0` do **not** upload separate artifacts — consumers use **`2.0.0`** coordinates. Pre-2.0 Central versions (`1.0.0`, `1.1.0`) remain available. Create [GitHub Releases manually](RELEASE.md#3-create-github-release-manual).

---

## 1. One-time setup (Sonatype)

1. Create an account at [central.sonatype.com](https://central.sonatype.com).
2. **Register namespace** `studio.syncforge` and verify domain ownership with a DNS TXT record on `syncforge.studio`.
3. Generate a **Portal API token** (username + password) for publishing.
4. Create or export a **GPG key** for artifact signing (armored private key for CI).

---

## 2. POM metadata (repo)

These live in [gradle.properties](../gradle.properties) and are applied by
[gradle/publish-convention.gradle.kts](../gradle/publish-convention.gradle.kts):

| Property | Value |
|----------|-------|
| `syncforge.group` | `studio.syncforge` |
| `syncforge.pom.url` | `https://github.com/Arsenoal/syncforge` |
| `syncforge.pom.scm.connection` | `scm:git:git://github.com/Arsenoal/syncforge.git` |
| `syncforge.pom.scm.developerConnection` | `scm:git:ssh://github.com/Arsenoal/syncforge.git` |
| License | Apache 2.0 |

Version is set from the git tag in CI (`v2.0.0` → `syncforge.version=2.0.0`).

---

## 3. GitHub Actions secrets

In **Arsenoal/syncforge** → Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Portal token password |
| `SIGNING_IN_MEMORY_KEY_B64` | **Recommended** — base64 of armored private key (single line, reliable in CI) |
| `SIGNING_IN_MEMORY_KEY` | Alternative — full armored private key (multi-line) |
| `SIGNING_IN_MEMORY_KEY_ID` | Optional override — **8 hex chars only** (e.g. `1DF1CDEB`). Not your Sonatype username. CI auto-detects if omitted. |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Optional passphrase |

Generate base64 secret locally:

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID | base64 -w0   # Linux
gpg --armor --export-secret-keys YOUR_KEY_ID | base64       # macOS
```

Paste the **single-line** output into `SIGNING_IN_MEMORY_KEY_B64`. Secret name must be exact — not `SIGN_IN_MEMORY_KEY`.

---

## 4. Local dry-run (optional, before tagging)

Add to **`~/.gradle/gradle.properties`** (never commit):

```properties
mavenCentralUsername=<portal-token-username>
mavenCentralPassword=<portal-token-password>
signing.inMemoryKey=<armored-private-key>
signing.inMemoryKeyId=<optional>
signing.inMemoryKeyPassword=<optional>
mavenCentralPublishing=true
signAllPublications=true
```

From the repo root:

```bash
# Publish to ~/.m2/repository and compile minimal consumer app
./gradlew verifyConsumerSmoke

# Staging upload to Maven Central (requires credentials above)
./gradlew publishAllToMavenCentral
```

Close and release the staging repository in the Sonatype Central Portal UI after a successful upload.

---

## 5. Release (manual)

Full maintainer flow: [RELEASE.md](RELEASE.md). Summary:

1. Ensure `main` is green (`verifyReleaseSignOff`, E2E jobs on CI).
2. Bump `syncforge.version` + `CHANGELOG.md` on `main`, tag, and push:

```bash
git tag v2.0.0
git push origin v2.0.0
```

3. **Create the GitHub Release manually** in the repository UI (tag push does not create one).
4. **Actions → Publish Release → Run workflow** — tag defaults to `v2.0.0`; confirm or enter the release tag. The job checks out that tag and runs on `macos-latest`:
   - **All versions:** compiles Android, JVM, iOS Simulator, macOS targets; runs JVM tests
   - **`< 2.0.0`:** Maven Central and iOS SPM upload **skipped** (policy notice in job log)
   - **`>= 2.0.0`:** publishes library artifacts via `publishAllToMavenCentral` and `publishIosSpmArtifacts` (when 1.3-04 is implemented)

### 5a. Pre-2.0 tags (1.x)

No Sonatype steps. Integrate unpublished builds locally:

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

Or point your app at the repo with Gradle `includeBuild("../syncforge")` / composite builds.

### 5b. Maven Central publish (2.0.0+ only)

4. CI runs `./gradlew finalizeMavenCentralStaging` so uploads appear under **Deployments**
   (required for Gradle `maven-publish` — see [OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)).
5. In Sonatype Central Portal → **Deployments**: **Publish every VALIDATED deployment**.
   KMP macOS/iOS targets often create a **second** deployment (~11 components) alongside the main set (~39).
   Publish **both** — otherwise native KMP artifacts stay off Maven Central.
6. Wait for Central sync (~15–60 minutes). CI does **not** poll Maven Central during publish.
7. **Actions → Verify Maven Central Release → Run workflow** — enter the version (e.g. `2.0.0`).
   This checks required POMs on `repo1.maven.org` and runs consumer-smoke from Central only.

**Maintainer override (local only):** `-PallowPre2MavenCentralPublish=true` bypasses the 2.0 gate on `publishAllToMavenCentral` — do not use for routine 1.x releases.

---

## 6. Published artifacts

| Artifact | Role |
|----------|------|
| `studio.syncforge:syncforge` | Main KMP library |
| `studio.syncforge:syncforge-annotations` | KSP annotations |
| `studio.syncforge:syncforge-ksp` | KSP processor |
| `studio.syncforge:syncforge-persistence` | SQLDelight persistence |
| `studio.syncforge:syncforge-android-deps` | Room / WorkManager / serialization bundle |
| `studio.syncforge:syncforge-network-ktor` | Optional Ktor REST adapter (catalog-listed, not transitive) |
| `studio.syncforge:syncforge-store-room` | Optional Room → `EntityStore` adapter |
| `studio.syncforge:syncforge-store-inmemory` | Optional in-memory `EntityStore` for tests |
| `studio.syncforge:syncforge-integration-koin` | Optional Koin DI helpers |
| `studio.syncforge:syncforge-integration-hilt` | Optional Hilt DI helpers |
| `studio.syncforge:syncforge-catalog` | Gradle version catalog — pins core + optional modules + `studio.syncforge.android` plugin |
| `studio.syncforge:syncforge-gradle-plugin` | Gradle plugin implementation (Maven `pluginMaven`) |
| `studio.syncforge.android:studio.syncforge.android.gradle.plugin` | Plugin marker for `id("studio.syncforge.android")` |

Consumer setup: [GETTING_STARTED.md](GETTING_STARTED.md) Step 0.

---

## 7. Post-publish verification

### Bump consumer-smoke Maven Central pins

After the release is live on Central, update:

- `consumer-smoke/android-minimal/gradle.properties` (`syncforge.version` — catalog import version)

Then push so CI `verifyConsumerSmokeMavenCentral` validates the new coordinates. Until Central syncs, `verifySignOffMatrix` may fail on the Maven Central artifact check.

### Resolve from Maven Central

```bash
# Example — after Central sync (may take minutes)
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-catalog/2.0.0/syncforge-catalog-2.0.0.toml" | head -1
```

### Consumer smoke against Central

In [consumer-smoke/android-minimal/settings.gradle.kts](../consumer-smoke/android-minimal/settings.gradle.kts), **remove** `mavenLocal()` from `pluginManagement` and `dependencyResolutionManagement` repositories (keep `google()`, `gradlePluginPortal()`, `mavenCentral()`).

```bash
./gradlew -p consumer-smoke/android-minimal :app:compileDebugKotlin
```

Restore `mavenLocal()` for day-to-day local publish testing.

---

## 8. RC → 1.0.0

| Step | Action |
|------|--------|
| Soak | ✅ `0.9.0-rc.5` complete (shipped as `1.0.0`) |
| Release prep | ✅ Repo bumped to `1.0.0`; `CHANGELOG` updated |
| Stable | ✅ Maven Central `1.0.0` published; tag `v1.0.0`; [verify run #1](https://github.com/Arsenoal/syncforge/actions/runs/28852404760) |

```bash
git tag v1.0.0
git push origin v1.0.0
# Portal: Publish all VALIDATED deployments (usually 2)
# After Central sync: Actions → Verify Maven Central Release (version 1.0.0)
# Or locally:
./gradlew verifyReleaseSignOff
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.0.0
./gradlew verifyConsumerSmokeMavenCentral
```
| Sign-off | [ROADMAP_1_0_TO_2_0.md § 1.0.0 sign-off](ROADMAP_1_0_TO_2_0.md#100-sign-off-checklist) |

---

## 9. 1.0.0 → 1.1.0

| Step | Action |
|------|--------|
| Release prep | ✅ Repo bumped to `1.1.0`; `CHANGELOG [1.1.0]`; catalog optional artifacts verified |
| Pre-tag | ✅ `./gradlew verifySignOffMatrix` |
| Tag | ✅ `v1.1.0` pushed |
| Publish | ✅ Publish Release + portal Publish (both VALIDATED deployments) |
| Post-publish | ✅ Consumer-smoke pins at `1.1.0`; artifacts + consumer smoke verified locally |

```bash
./gradlew verifySignOffMatrix
git tag v1.1.0
git push origin v1.1.0
# After Central sync:
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=1.1.0
./gradlew verifyConsumerSmokeMavenCentral
```

| Sign-off | [ROADMAP_1_0_TO_2_0.md § 1.1.0 sign-off](ROADMAP_1_0_TO_2_0.md#110-sign-off-checklist) |

---

## 10. 1.1.0 → 2.0.0

| Step | Action |
|------|--------|
| Release prep | ✅ Repo bumped to `2.0.0`; `CHANGELOG [2.0.0]`; catalog lists all optional modules; BOM removed from build |
| Pre-tag | ✅ `./gradlew verifySignOffMatrix` |
| Tag | ✅ `v2.0.0` pushed |
| Publish | ✅ Publish Release + portal Publish (JVM/Android + KMP native deployments) |
| Post-publish | ✅ Consumer-smoke pins at `2.0.0`; artifacts on `repo1.maven.org` |

```bash
./gradlew verifySignOffMatrix
git tag v2.0.0
git push origin v2.0.0
# After Central sync:
./gradlew verifyMavenCentralArtifacts -PverifyMavenCentralVersion=2.0.0
./gradlew verifyConsumerSmokeMavenCentral
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-catalog/2.0.0/syncforge-catalog-2.0.0.toml" | head -1
```

**Consumer migration:** [UPGRADE_1_1_TO_2_0.md](UPGRADE_1_1_TO_2_0.md) — import `syncforge-catalog:2.0.0`; drop `syncforge-bom`.

**Not on Central at 2.0.0:** browser `js` / `SyncForge.web { }` (monorepo-only); iOS SPM / XCFramework (target **`2.0.1+`**).

| Sign-off | [ROADMAP_1_0_TO_2_0.md § 2.0.0 sign-off](ROADMAP_1_0_TO_2_0.md#200-sign-off-checklist) |

### Next: monorepo `2.0.1` (not published yet)

`main` is at **`syncforge.version=2.0.1`** (EntityStore / REST / transport `@OptIn` graduation). To publish:

1. Keep `consumer-smoke` at **`2.0.0`** until Central has `2.0.1`.
2. `./gradlew verifySignOffMatrix`
3. Tag `v2.0.1` → Publish Release → portal Publish → Verify Maven Central Release
4. Only then bump consumer pins and install snippets to `2.0.1`

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `syncforge-android-deps` missing on Central | Ensure `:syncforge-android-deps:publish` is in `publishAllToMavenCentral` (see [build.gradle.kts](../build.gradle.kts)) |
| iOS/macOS compile fails on tag | `publish-release.yml` must run on `macos-latest` (already configured) |
| Consumer smoke fails before publish | Consumer-smoke Maven Central pins should match the **last live Central version** (`2.0.0` today) — bump pins only **after** the new release syncs |
| Consumer smoke fails after publish | Bump `consumer-smoke/android-minimal/gradle/libs.versions.toml` **and** `gradle.properties` (`syncforge.version`) **after** Central sync |
| Artifact missing after publish | Wait for Central sync (`verifyMavenCentralArtifacts`), or check Sonatype **Component Coordinates** before clicking **Publish** |
| Deployment **FAILED** with "already exists" | Maven Central does not allow re-uploading the same version — bump `syncforge.version`, tag a new release, and run **Publish Release** again |
| CI publish succeeded but **Deployments** is empty | Re-run **Actions → Publish Release** (workflow_dispatch). It drops stale staging, publishes all artifacts, and runs the OSSRH finalize script automatically |
| `finalizeMavenCentralStaging` **Read timed out** | Sonatype staging API can be slow after large KMP uploads. **Start a new workflow** — do **not** use GitHub **Re-run failed jobs** (that reuses the old 60s script). Use **Actions → Finalize Maven Central Staging**, or locally: `FINALIZE_CURRENT_IP=false MAVEN_CENTRAL_USERNAME=… MAVEN_CENTRAL_PASSWORD=… ./gradlew finalizeMavenCentralStaging`. Defaults: 600s read timeout, 5 retries; current-IP timeouts fall through to orphan repo promotion. |
| Plugin not found | `pluginManagement { repositories { mavenCentral(); gradlePluginPortal(); google() } }` |
| Signing secret empty in CI | Use `SIGNING_IN_MEMORY_KEY_B64` on `github.com/Arsenoal/syncforge` → Settings → Secrets; name must be exact |
| `Unable to read secret key from file` | CI converts armored key → binary via `gpg --dearmor`; store armored key in secrets, not binary |
| `key ID must be in a valid form` | `SIGNING_IN_MEMORY_KEY_ID` must be 8 hex chars (`1DF1CDEB`), not Portal token username — or delete it and let CI auto-detect |
| Signing errors in CI | Upload public key to keys.openpgp.org; use full armored private key or base64 secret |

---

## Related

- [GETTING_STARTED.md](GETTING_STARTED.md) — consumer Gradle snippets
- [consumer-smoke/README.md](../consumer-smoke/README.md) — minimal Maven-only test app
- [REST_API.md](REST_API.md) — backend contract (separate from Maven publish)