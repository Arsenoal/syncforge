# Compose Multiplatform UI

Shared Compose UI in `:syncforge` for conflict resolution across Android, JVM/desktop, and Apple (Kotlin/Native) targets.

**Android-only (experimental):** `SyncDebugLauncher`, `SyncDebugPanel` — see [MODULES.md](MODULES.md).

---

## Shared conflict UI (1.3-05)

| Composable | Package | Targets |
|------------|---------|---------|
| `SyncConflictChip` | `dev.syncforge.compose` | Android, JVM, iOS, macOS (via `composeMain`) |
| `SyncConflictResolutionSheet` | `dev.syncforge.compose` | Same — cross-platform `AlertDialog` |

Source: [`syncforge/src/composeMain/.../SyncConflictUi.kt`](../syncforge/src/composeMain/kotlin/dev/syncforge/compose/SyncConflictUi.kt)

### Android (Compose)

Same wiring as before — see [`:sample` TasksScreen](../sample/src/main/kotlin/dev/syncforge/sample/tasks/TasksScreen.kt):

```kotlin
import dev.syncforge.compose.SyncConflictChip
import dev.syncforge.compose.SyncConflictResolutionSheet

SyncConflictResolutionSheet(
    conflict = activeConflict,
    localContent = { /* entity preview */ },
    remoteContent = { /* server preview */ },
    onKeepLocal = { /* resolveConflict(KeepLocal) */ },
    onAcceptRemote = { /* resolveConflict(AcceptRemote) */ },
    onDismiss = { /* clear selection */ },
)
```

Instrumented tests use `conflict_keep_local` and `conflict_accept_remote` test tags (unchanged).

### JVM / desktop

Minimal demo window:

```bash
./gradlew :sample-desktop:runComposeConflictDemo
```

CLI sample (`:sample-desktop:run`) remains the primary desktop smoke path.

### iOS / macOS (Kotlin)

Conflict composables compile into the `SyncForge` KMP framework on Apple targets. Use from **Compose Multiplatform** iOS/macOS apps with `MaterialTheme` and your entity preview composables.

SwiftUI apps (`ios-sample/`) continue to use native UI — see [SWIFT_INTEROP.md](SWIFT_INTEROP.md). To embed CMP in UIKit/SwiftUI, host a `ComposeUIViewController` (out of scope for the sample app).

---

## Source sets

```
commonMain          — SyncStatusUiModel, toUiModel()
composeMain         — SyncConflictChip, SyncConflictResolutionSheet (CMP Material3)
androidMain         — SyncDebugPanel, SyncStatusCompose helpers (androidx Compose)
```

`composeMain` depends on `commonMain`; `androidMain`, `jvmMain`, and `appleMain` depend on both `syncPersistenceMain` and `composeMain`.

---

## Related docs

- [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) — strategy matrix and resolution flow
- [RECIPES.md](RECIPES.md) — copy-paste conflict sheet wiring
- [DESKTOP_SETUP.md](DESKTOP_SETUP.md) — `SyncForge.desktop { }`
- [IOS_SETUP.md](IOS_SETUP.md) — KMP / Swift integration