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

**No new semver rollouts until `v2.0.0`** — features ship on `main` with CHANGELOG
`[Unreleased]` entries. Maven Central, SPM, GitHub Releases, and new version tags resume at
2.0. Until then, integrate via `publishAllToMavenLocal` or composite build. See
[docs/RELEASE.md](docs/RELEASE.md).

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).