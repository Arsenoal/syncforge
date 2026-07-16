# Contributing to SyncForge

Thanks for your interest in SyncForge. Feedback, bug reports, docs fixes, and
pull requests are welcome.

## Ways to help

* **Try the library** from Maven Central and report integration issues
* **Open an issue** with a minimal repro (Android, KMP, or backend contract)
* **Improve docs** in `docs/` or `README.md`
* **Send a PR** with a focused change and tests when applicable

## Development setup

Requirements: JDK 17, Android SDK (for Android modules), Gradle wrapper in repo.

```bash
git clone https://github.com/Arsenoal/syncforge.git
cd syncforge
./gradlew verifySignOffMatrix
```

Useful tasks:

| Task | Purpose |
|------|---------|
| `./gradlew verifySignOffMatrix` | Pre-tag sign-off: `verifyReleaseSignOff` + `verifyConsumerSmokeMavenCentral` |
| `./gradlew verifyReleaseSignOff` | JVM + Android tests, consumer smoke (mavenLocal) only |
| `./gradlew verifyConsumerSmokeMavenCentral` | Consumer app from Maven Central only |
| `./gradlew androidE2e` | Emulator E2E (Linux CI) |
| `./gradlew webE2e` | Browser push/pull smoke (headless Chrome; nightly CI) |
| `./gradlew publishAllToMavenLocal` | Local Maven publish for integration |

See [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) for consumer setup.

## Pull requests

1. Fork and branch from `main` (`fix/…`, `docs/…`, `feat/…`)
2. Keep changes scoped — one concern per PR
3. Run `./gradlew verifyReleaseSignOff` (or the smallest relevant task) before opening
4. Describe **what** changed and **why** in the PR body
5. Link related issues when applicable

Pre-1.0 API changes are still possible but should be discussed in an issue first.

## Releases

**Latest release:** `v2.0.0` on Maven Central. **Monorepo (`main`):** `2.0.1` (not on Central until
tagged). Patch releases (`v2.0.x`) follow [docs/RELEASE.md](docs/RELEASE.md): bump
`syncforge.version` in `gradle.properties`, update `CHANGELOG.md`, tag, and run **Publish Release**.

Consumer install examples and `consumer-smoke` Central pins stay on the **last live Central
version** (`2.0.0`) until the next publish syncs. Browser `js` and iOS SPM remain outside the
default Central set — see [docs/MAVEN_PUBLISH.md](docs/MAVEN_PUBLISH.md).

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).