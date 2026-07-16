# SyncForge Firebase transport

`SyncDeltaStore` implementation for [Firebase](https://firebase.google.com) Firestore via Cloud Functions HTTPS endpoints.

| Artifact | Role |
|----------|------|
| `:syncforge-transport-core` | `SyncDeltaStore` + `DeltaStoreSyncTransport` |
| `:syncforge-transport-firebase` | `FirebaseSyncDeltaStore` (this module) |

## Setup

1. Deploy Firestore rules, indexes, and Cloud Functions:

```bash
cd syncforge-transport-firebase/firebase
npm install --prefix functions
firebase deploy --only functions,firestore
```

Functions: `syncforgePush`, `syncforgePull` — same push/pull contract as `:syncforge-server` `SyncStore`.

2. Wire the transport in your app:

```kotlin
val config = FirebaseSyncConfig.cloudFunctions(
    projectId = "your-firebase-project",
    region = "us-central1",
    idToken = { Firebase.auth.currentUser?.getIdToken(false)?.await() },
)

SyncForge.android(this) {
    transport(DeltaStoreSyncTransport(FirebaseSyncDeltaStore(config)))
    registry(SyncForgeHandlers.registry(taskDao))
}
```

For Cloud Functions v2 / Cloud Run URLs, set explicit URLs:

```kotlin
FirebaseSyncConfig(
    pushUrl = "https://syncforgepush-….run.app",
    pullUrl = "https://syncforgepull-….run.app",
    idToken = { … },
)
```

## Firestore layout

| Path | Purpose |
|------|---------|
| `sync_entity/{entityType}:{entityId}` | Entity delta records |
| `metadata/version_counter` | Global `serverVersion` counter |

See `FirebaseFirestoreSchema` in source.

## Realtime

Attach a Firestore snapshot listener on `sync_entity` and call `syncManager.sync()` on changes. See `FirebaseListenerPatterns` in source.

## Auth

Pass a Firebase Auth ID token via `idToken` so Functions can verify callers (add verification in functions for production). Use `auth(SyncAuthProvider)` on the SyncForge DSL for token refresh alongside transport wiring.