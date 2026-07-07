# Maven Central publish checklist

Step-by-step guide for publishing SyncForge to Maven Central. Current stable release: **1.1.0**.

**Repository:** [github.com/Arsenoal/syncforge](https://github.com/Arsenoal/syncforge)  
**Group ID:** `studio.syncforge` (namespace verified via DNS on `syncforge.studio`)  
**Workflow:** [.github/workflows/publish-release.yml](../.github/workflows/publish-release.yml) (runs on `v*` tags)

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

Version is set from the git tag in CI (`v1.0.0` → `syncforge.version=1.0.0`).

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

## 5. Publish via tag (CI)

1. Ensure `main` is green (CI runs `verifyConsumerSmoke` on linux).
2. Create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

3. Watch **Actions** → **Publish Release** on `macos-latest`:
   - Compiles Android, JVM, iOS Simulator, macOS targets
   - Runs `:syncforge:jvmTest` and `:syncforge-persistence:jvmTest`
   - Publishes **all** library artifacts via `publishAllToMavenCentral`

   To re-run manually (e.g. after a workflow fix): **Actions → Publish Release → Run workflow**, enter the tag (`v1.0.0`). Each run uploads the full artifact set for that version — use a **new version tag** if Central already has that release.

4. CI runs `.github/scripts/finalize-maven-central-staging.sh` so uploads appear under **Deployments**
   (required for Gradle `maven-publish` — see [OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)).
5. In Sonatype Central Portal → **Deployments**: **Publish every VALIDATED deployment**.
   KMP macOS/iOS targets often create a **second** deployment (~11 components) alongside the main set (~39).
   Publish **both** — otherwise native KMP artifacts stay off Maven Central.
6. Wait for Central sync (~15–60 minutes). CI does **not** poll Maven Central during publish.
7. **Actions → Verify Maven Central Release → Run workflow** — enter the version (e.g. `1.0.0`).
   This checks required POMs on `repo1.maven.org` and runs consumer-smoke from Central only.

---

## 6. Published artifacts

| Artifact | Role |
|----------|------|
| `studio.syncforge:syncforge` | Main KMP library |
| `studio.syncforge:syncforge-annotations` | KSP annotations |
| `studio.syncforge:syncforge-ksp` | KSP processor |
| `studio.syncforge:syncforge-persistence` | SQLDelight persistence |
| `studio.syncforge:syncforge-android-deps` | Room / WorkManager / serialization bundle |
| `studio.syncforge:syncforge-network-ktor` | Optional Ktor REST adapter (BOM-listed, not transitive) |
| `studio.syncforge:syncforge-store-room` | Optional Room → `EntityStore` adapter |
| `studio.syncforge:syncforge-store-inmemory` | Optional in-memory `EntityStore` for tests |
| `studio.syncforge:syncforge-integration-koin` | Optional Koin DI helpers |
| `studio.syncforge:syncforge-integration-hilt` | Optional Hilt DI helpers |
| `studio.syncforge:syncforge-bom` | Version alignment BOM (core + optional modules above) |
| `studio.syncforge:syncforge-gradle-plugin` | Gradle plugin implementation (Maven `pluginMaven`) |
| `studio.syncforge.android:studio.syncforge.android.gradle.plugin` | Plugin marker for `id("studio.syncforge.android")` |

Consumer setup: [GETTING_STARTED.md](GETTING_STARTED.md) Step 0.

---

## 7. Post-publish verification

### Bump consumer-smoke Maven Central pins

After the release is live on Central, update:

- `consumer-smoke/android-minimal/gradle.properties` (`syncforge.version`)
- `consumer-smoke/android-minimal/gradle/libs.versions.toml` (`syncforge`)

Then push so CI `verifyConsumerSmokeMavenCentral` validates the new coordinates. Until Central syncs, `verifySignOffMatrix` may fail on the Maven Central artifact check.

### Resolve from Maven Central

```bash
# Example — after Central sync (may take minutes)
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-bom/1.0.0/syncforge-bom-1.0.0.pom" | head -1
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
bash .github/scripts/verify-maven-central-artifacts.sh 1.0.0
./gradlew verifyConsumerSmokeMavenCentral
```
| Sign-off | P0 checklist in `SyncForge-1.0-P0.docx` |

---

## 9. 1.0.0 → 1.1.0

| Step | Action |
|------|--------|
| Release prep | ✅ Repo bumped to `1.1.0`; `CHANGELOG [1.1.0]`; BOM optional artifacts verified |
| Pre-tag | `./gradlew verifySignOffMatrix` |
| Tag | `git tag v1.1.0 && git push origin v1.1.0` |
| Publish | Actions → Publish Release (tag `v1.1.0`); portal Publish all VALIDATED deployments |
| Post-publish | Bump `consumer-smoke/android-minimal` pins to `1.1.0`; Actions → Verify Maven Central Release |

```bash
./gradlew verifySignOffMatrix
git tag v1.1.0
git push origin v1.1.0
# After Central sync:
bash .github/scripts/verify-maven-central-artifacts.sh 1.1.0
./gradlew verifyConsumerSmokeMavenCentral
```

| Sign-off | [ROADMAP_1_0_TO_2_0.md § 1.1.0 sign-off](ROADMAP_1_0_TO_2_0.md#110-sign-off-checklist) |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `syncforge-android-deps` missing on Central | Ensure `:syncforge-android-deps:publish` is in `publishAllToMavenCentral` (see [build.gradle.kts](../build.gradle.kts)) |
| iOS/macOS compile fails on tag | `publish-release.yml` must run on `macos-latest` (already configured) |
| Consumer smoke fails before publish | Consumer-smoke Maven Central pins stay on the **last live Central version** until the new release syncs — do not bump pins when tagging |
| Consumer smoke fails after publish | Bump `consumer-smoke/android-minimal/gradle/libs.versions.toml` **and** `gradle.properties` (`syncforge.version`) **after** Central sync |
| Artifact missing after publish | Wait for Central sync (`verify-maven-central-artifacts.sh`), or check Sonatype **Component Coordinates** before clicking **Publish** |
| Deployment **FAILED** with "already exists" | Maven Central does not allow re-uploading the same version — bump `syncforge.version`, tag a new release, and run **Publish Release** again |
| CI publish succeeded but **Deployments** is empty | Re-run **Actions → Publish Release** (workflow_dispatch). It drops stale staging, publishes all artifacts, and runs the OSSRH finalize script automatically |
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