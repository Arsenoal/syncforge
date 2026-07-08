# Release process

How maintainers ship SyncForge. **Development continues on `main`** — there are **no new semver rollouts** (tags, GitHub Releases, Maven Central, or SPM) until **`v2.0.0`**.

**Related docs:** [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md) (Maven Central details), [IOS_SETUP.md](IOS_SETUP.md) (local KMP iOS integration).

---

## Distribution policy (now → 2.0)

| Channel | Until `v2.0.0` | From `v2.0.0` |
|---------|----------------|---------------|
| **Semver tags / GitHub Releases** | **No new rollouts** — work lands on `main`; [CHANGELOG](../CHANGELOG.md) `[Unreleased]` | First public rollout of the 1.x line |
| **Maven Central** | No new uploads (1.0.0 / 1.1.0 remain) | Full KMP artifact set |
| **iOS SPM / XCFramework** | KMP frameworks locally only | Planned with 1.3-04 |

Integrate pre-2.0 builds via git clone, `publishAllToMavenLocal`, or Gradle composite/`includeBuild`. iOS uses `linkIosFrameworksForXcode` / Xcode Run Script — see [IOS_SETUP.md](IOS_SETUP.md).

---

## Pre-2.0 development (current mode)

1. Merge features to `main`.
2. Keep [CHANGELOG.md](../CHANGELOG.md) under `[Unreleased]`.
3. Run `./gradlew verifySignOffMatrix` and ensure CI is green (`androidE2e`, `iosE2e`, `desktopE2e`).
4. Do **not** tag `v1.3.0`, `v1.4.0`, etc. or publish GitHub Releases for interim milestones.

Optional: **Actions → Publish Release → Run workflow** with an **existing** tag (e.g. `v1.2.0`) to validate macOS compile — not required for day-to-day development.

---

## 2.0.0 release checklist

When the 2.0 milestone is ready:

```bash
./gradlew verifySignOffMatrix
```

1. Bump `syncforge.version` to `2.0.0` in [gradle.properties](../gradle.properties).
2. Move [CHANGELOG.md](../CHANGELOG.md) `[Unreleased]` into `[2.0.0]`.
3. Merge to `main`, then tag and push:

```bash
git tag v2.0.0
git push origin v2.0.0
```

4. **GitHub → Releases → Draft a new release** — choose `v2.0.0`, paste CHANGELOG notes, publish.
5. **Actions → Publish Release → Run workflow** — tag `v2.0.0` (macOS compile + Maven Central + SPM when 1.3-04 ships).

Follow [MAVEN_PUBLISH.md § 5b](MAVEN_PUBLISH.md#5b-maven-central-publish-200-only) (Central Portal → Publish deployments, then **Verify Maven Central Release**).

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