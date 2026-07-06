# Maven Central publish checklist

Step-by-step guide for publishing SyncForge to Maven Central. Use for the **0.9.0-rc.5** release
and the **1.0.0** stable release.

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

Version is set from the git tag in CI (`v0.9.0-rc.5` → `syncforge.version=0.9.0-rc.5`).

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
git tag v0.9.0-rc.5
git push origin v0.9.0-rc.5
```

3. Watch **Actions** → **Publish Release** on `macos-latest`:
   - Compiles Android, JVM, iOS Simulator, macOS targets
   - Runs `:syncforge:jvmTest` and `:syncforge-persistence:jvmTest`
   - Runs `publishAllToMavenCentral`

4. CI runs `.github/scripts/finalize-maven-central-staging.sh` so uploads appear under **Deployments**
   (required for Gradle `maven-publish` — see [OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)).
5. In Sonatype Central Portal: **Publish** the deployment (or Close → Release on legacy flows).

---

## 6. Published artifacts

| Artifact | Role |
|----------|------|
| `studio.syncforge:syncforge` | Main KMP library |
| `studio.syncforge:syncforge-annotations` | KSP annotations |
| `studio.syncforge:syncforge-ksp` | KSP processor |
| `studio.syncforge:syncforge-persistence` | SQLDelight persistence |
| `studio.syncforge:syncforge-android-deps` | Room / WorkManager / serialization bundle |
| `studio.syncforge:syncforge-bom` | Version alignment BOM |
| `studio.syncforge:syncforge-gradle-plugin` | Gradle plugin implementation (Maven `pluginMaven`) |
| `studio.syncforge.android:studio.syncforge.android.gradle.plugin` | Plugin marker for `id("studio.syncforge.android")` |

Consumer setup: [GETTING_STARTED.md](GETTING_STARTED.md) Step 0.

---

## 7. Post-publish verification

### Bump consumer-smoke Maven Central pins

After the release is live on Central, update:

- `consumer-smoke/android-minimal/gradle.properties` (`syncforge.version`)
- `consumer-smoke/android-minimal/gradle/libs.versions.toml` (`syncforge`)

Then push so CI `verifyConsumerSmokeMavenCentral` validates the new coordinates. Until then, pins stay on the previous published RC (e.g. keep `0.9.0-rc.4` until `0.9.0-rc.5` syncs to `repo1.maven.org`).

### Resolve from Maven Central

```bash
# Example — after Central sync (may take minutes)
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-bom/0.9.0-rc.5/syncforge-bom-0.9.0-rc.5.pom" | head -1
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
| Soak | Let `0.9.0-rc.5` sit; CI + optional external dogfood |
| Fixes | Tag `v0.9.0-rc.5` if needed |
| Stable | Bump version, tag `v1.0.0`, repeat publish + verification |
| Sign-off | P0 checklist in `SyncForge-1.0-P0.docx` |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `syncforge-android-deps` missing on Central | Ensure `:syncforge-android-deps:publish` is in `publishAllToMavenCentral` (see [build.gradle.kts](../build.gradle.kts)) |
| iOS/macOS compile fails on tag | `publish-release.yml` must run on `macos-latest` (already configured) |
| Consumer smoke fails before publish | Consumer-smoke Maven Central pins stay on the **last live Central version** until the new release syncs — do not bump pins when tagging |
| Consumer smoke fails after publish | Bump `consumer-smoke/android-minimal/gradle/libs.versions.toml` **and** `gradle.properties` (`syncforge.version`) **after** Central sync |
| `syncforge-bom` / `syncforge-ksp` missing from Sonatype deployment | Re-run **Publish Release** (workflow_dispatch) for the same tag after `main` includes explicit BOM/KSP publish steps; missing GAVs can usually be added without a new version |
| Publish workflow green but BOM 404 on Central | Wait for sync (`verify-maven-central-artifacts.sh`) or check Sonatype **Component Coordinates** for `syncforge-bom` before clicking **Publish** |
| CI publish succeeded but **Deployments** is empty | Re-run **Actions → Publish Release** (workflow_dispatch). It drops stale staging, publishes, and runs the OSSRH finalize script automatically |
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