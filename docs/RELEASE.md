# Release process

How maintainers ship SyncForge. **Current release line:** `v2.0.0` on Maven Central.

**Related docs:** [MAVEN_PUBLISH.md](MAVEN_PUBLISH.md) (Maven Central details), [IOS_SETUP.md](IOS_SETUP.md) (local KMP iOS integration).

---

## Distribution policy

| Channel | `v2.0.0+` |
|---------|-----------|
| **Git tag** | `v2.0.0`, `v2.0.1`, … |
| **Maven Central** | Full KMP artifact set via **Publish Release** workflow |
| **iOS SPM / XCFramework** | `publishIosSpmArtifacts` (may ship in `2.0.1` if stub at cut) |
| **Browser `js`** | Monorepo / composite only — not on Central |

Pre-2.0 monorepo tags (`v1.2.0`–`v1.6.0`) remain git milestones; consumers use **`2.0.0`** coordinates.

---

## Release checklist

```bash
./gradlew verifySignOffMatrix
```

1. Bump `syncforge.version` in [gradle.properties](../gradle.properties).
2. Update [CHANGELOG.md](../CHANGELOG.md).
3. Merge to `main`, then tag and push:

```bash
git tag v2.0.0
git push origin v2.0.0
```

4. **GitHub → Releases → Draft a new release** — choose the tag, paste CHANGELOG notes, publish.
5. **Actions → Publish Release → Run workflow** — default tag is `v2.0.0` (macOS compile + Maven Central + SPM when ready).

Follow [MAVEN_PUBLISH.md § 5b](MAVEN_PUBLISH.md#5b-maven-central-publish-200-only) (Central Portal → Publish deployments, then **Verify Maven Central Release**).

---

## Post-release

After Maven Central sync:

1. **Actions → Verify Maven Central Release** — version `2.0.0` (default).
2. Workflow bumps `consumer-smoke` pins and runs `verifyConsumerSmokeMavenCentral`.
3. Update [README.md](../README.md) / [ROADMAP.md](ROADMAP.md) if the live Central version changed.

Patch releases: bump `syncforge.version`, tag `v2.0.x`, repeat Publish Release with the new tag.

---

## Local integration (without publishing)

```bash
./gradlew publishAllToMavenLocal verifyConsumerSmoke
```

Or Gradle `includeBuild("../syncforge")` / composite builds. iOS: `linkIosFrameworksForXcode` — see [IOS_SETUP.md](IOS_SETUP.md).