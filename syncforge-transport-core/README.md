# SyncForge transport core

`SyncDeltaStore` port and `DeltaStoreSyncTransport` adapter for BaaS backends.

Implementation guide: [docs/CUSTOM_TRANSPORT.md](../docs/CUSTOM_TRANSPORT.md).

## Contract test kit (1.4-07)

Shared push/pull scenarios live in:

| Artifact | Role |
|----------|------|
| `dev.syncforge.network.contract.SyncPushPullContract` | DTO-level scenarios for REST `SyncStore` |
| `dev.syncforge.transport.contract.SyncDeltaStoreContract` | `SyncDeltaStore` scenarios |
| `InMemorySyncBackend` / `InMemorySyncDeltaStore` | Reference in-memory backend |
| `ContractSyncApi` | Wire into vendor API fakes in tests |

Run contract tests:

```bash
./gradlew :syncforge-transport-core:jvmTest \
  :syncforge-transport-supabase:jvmTest \
  :syncforge-transport-firebase:jvmTest \
  :syncforge-server:test
```

To test a new `SyncDeltaStore`, call `SyncDeltaStoreContract` scenarios from `commonTest` with a
spec-compliant API fake (see Supabase/Firebase `*ContractTest.kt`).