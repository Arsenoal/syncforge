# Maven Central publish checklist

Step-by-step guide for publishing SyncForge to Maven Central. Use for the **0.9.0-rc.3** release
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

Version is set from the git tag in CI (`v0.9.0-rc.3` → `syncforge.version=0.9.0-rc.3`).

---

## 3. GitHub Actions secrets

In **Arsenoal/syncforge** → Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Portal token password |
| `SIGNING_IN_MEMORY_KEY` | Armored GPG private key (multi-line PEM) |
| `SIGNING_IN_MEMORY_KEY_ID` | Optional key id (last 8 hex chars) |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | Optional passphrase |

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
git tag v0.9.0-rc.3
git push origin v0.9.0-rc.3
```

3. Watch **Actions** → **Publish Release** on `macos-latest`:
   - Compiles Android, JVM, iOS Simulator, macOS targets
   - Runs `:syncforge:jvmTest` and `:syncforge-persistence:jvmTest`
   - Runs `publishAllToMavenCentral`

4. In Sonatype Central Portal: **close** → **release** the staging repo (if not auto-released).

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

### Resolve from Maven Central

```bash
# Example — after Central sync (may take minutes)
curl -sI "https://repo1.maven.org/maven2/studio/syncforge/syncforge-bom/0.9.0-rc.3/syncforge-bom-0.9.0-rc.3.pom" | head -1
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
| Soak | Let `0.9.0-rc.3` sit; CI + optional external dogfood |
| Fixes | Tag `v0.9.0-rc.3` if needed |
| Stable | Bump version, tag `v1.0.0`, repeat publish + verification |
| Sign-off | P0 checklist in `SyncForge-1.0-P0.docx` |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `syncforge-android-deps` missing on Central | Ensure `:syncforge-android-deps:publish` is in `publishAllToMavenCentral` (see [build.gradle.kts](../build.gradle.kts)) |
| iOS/macOS compile fails on tag | `publish-release.yml` must run on `macos-latest` (already configured) |
| Consumer smoke fails after publish | Pin `syncforge.version` in `consumer-smoke/android-minimal/gradle/libs.versions.toml` to the tagged version |
| Plugin not found | `pluginManagement { repositories { mavenCentral(); gradlePluginPortal(); google() } }` |
| Signing errors in CI | Check `SIGNING_IN_MEMORY_KEY` newlines; use full armored block including headers |

---

## Related

- [GETTING_STARTED.md](GETTING_STARTED.md) — consumer Gradle snippets
- [consumer-smoke/README.md](../consumer-smoke/README.md) — minimal Maven-only test app
- [REST_API.md](REST_API.md) — backend contract (separate from Maven publish)