# Documentation folder

The **documentation index**, learning paths, demo GIF, and quick-start guides live on the
[repository README](../README.md) — that file is what GitHub shows on the project homepage.

---

## Files in `docs/`

```
docs/
├── README.md                 ← You are here (folder index; main hub is ../README.md)
├── GETTING_STARTED.md        ← Zero → working offline-first app (~10 min)
├── ANDROID_SETUP.md          ← Android DSL, SQLDelight default, Room migration
├── IOS_SETUP.md              ← iOS DSL, SQLDelight defaults, Swift integration
├── DESKTOP_SETUP.md          ← JVM desktop + native macOS DSL
├── RECIPES.md                ← How-to: merge, deferToUser, debug, observe status
├── CONFLICT_RESOLUTION.md    ← Strategies, lifecycle, Compose UI, decision guide
├── BEST_PRACTICES.md         ← Entity design, strategy choices, performance
├── KMP_MIGRATION.md          ← Room → SQLDelight, iOS targets, expect/actual plan
├── MODULES.md                ← Package-by-package API reference
├── REST_API.md               ← Backend push/pull contract
├── AUTH_API.md               ← Built-in register/login/refresh (Android flow + diagram)
├── ROADMAP.md                ← Phases, limitations, future work
├── ROADMAP_1_0_TO_2_0.md     ← Detailed plan: 1.0.0 through 2.0.0 (sign-off checklists)
├── MAVEN_PUBLISH.md          ← Maven Central publish + verify workflow
└── images/                   ← README demo GIF (+ recording guide)
```

---

## Contributing to docs

When adding a feature, update in this order:

1. [CHANGELOG.md](../CHANGELOG.md)
2. Relevant guide (Getting Started / Recipes / Conflict Resolution)
3. [MODULES.md](MODULES.md) API reference
4. [README.md](../README.md) — Start here table or sample scenario row (one line max per feature)