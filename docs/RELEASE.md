# Release process

How maintainers cut semver releases for SyncForge. **Nothing runs automatically on tag push** — GitHub Releases and CI publish are manual.

**Related docs:** [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md) (Maven Central details), [IOS_SETUP.md](IOS_SETUP.md) (local KMP iOS integration).

---

## Distribution policy (1.x → 2.0)

| Channel | Before `v2.0.0` | From `v2.0.0` |
|---------|-----------------|---------------|
| **Maven Central** | Not published (validation only) | Full KMP artifact set |
| **iOS SPM / XCFramework** | Not published (KMP frameworks locally) | Planned with 1.3-04 |
| **GitHub Release** | Manual in UI | Manual in UI |

Integrate unpublished 1.x builds via git clone, `publishAllToMavenLocal`, or Gradle composite/`includeBuild`. iOS uses `linkIosFrameworksForXcode` / Xcode Run Script — see [IOS_SETUP.md](IOS_SETUP.md).

Existing Maven Central versions (1.0.0, 1.1.0) remain available.

---

## 1. Pre-release checklist

On `main`:

```bash
./gradlew verifySignOffMatrix
```

Ensure CI is green (`androidE2e`, `iosE2e`, `desktopE2e` as applicable).

1. Bump `syncforge.version` in [gradle.properties](../gradle.properties).
2. Move [CHANGELOG.md](../CHANGELOG.md) entries from `[Unreleased]` into a new version section.
3. Merge to `main`.

---

## 2. Tag locally and push

```bash
git tag v1.2.0
git push origin v1.2.0
```

Pushing a tag **does not** trigger CI publish or create a GitHub Release.

---

## 3. Create GitHub Release (manual)

1. Open **GitHub → Releases → Draft a new release**.
2. Choose the tag (e.g. `v1.2.0`).
3. Title: `v1.2.0` (or a short headline).
4. Paste the matching **CHANGELOG** section as release notes.
5. Publish the release.

Repeat for every semver milestone when you want a visible release on GitHub.

---

## 4. Run Publish Release workflow (optional but recommended)

**Actions → Publish Release → Run workflow**

| Input | Example |
|-------|---------|
| Tag | `v1.2.0` |

The workflow checks out **that tag** (not `main`) and runs on `macos-latest`:

| Version | What runs |
|---------|-----------|
| **&lt; 2.0.0** | Compile all KMP targets + JVM tests |
| **≥ 2.0.0** | Above + Maven Central upload + `publishIosSpmArtifacts` (when 1.3-04 is implemented) |

For 2.0+ after a successful run, follow [MAVEN_PUBLISH.md § 5b](MAVEN_PUBLISH.md#5b-maven-central-publish-200-only) (Central Portal → Publish deployments, then **Verify Maven Central Release**).

Re-run the workflow anytime with the same tag (e.g. after fixing publish scripts on a newer commit — note the workflow still builds **the tagged tree**).

---

## 5. Post-release (2.0.0+ only)

After Maven Central sync:

1. **Actions → Verify Maven Central Release** — enter the version.
2. Update `consumer-smoke/android-minimal` version pins — see [MAVEN_PUBLISH.md § 7](MAVEN_PUBLISH.md#7-post-publish-verification).

---

## Maintainer overrides (local only)

| Property | Purpose |
|----------|---------|
| `-PallowPre2MavenCentralPublish=true` | Bypass Maven Central 2.0 gate |
| `-PallowPre2IosSpmPublish=true` | Bypass iOS SPM 2.0 gate |

Do not use for routine 1.x releases.