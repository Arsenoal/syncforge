# Rate limiting and backoff policies

SyncForge is designed to be **server-friendly**: failed pushes retry with configurable backoff, HTTP `429` responses are treated as retryable, and clients can throttle how often they call sync.

## Backoff policy

Configure retry delays via [SyncBackoffPolicy](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncBackoffPolicy.kt) on [SyncConfig](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncConfig.kt):

| Strategy | Delay formula |
|----------|---------------|
| `EXPONENTIAL` (default) | `baseDelay × 2^attempt`, capped at `maxDelay` |
| `LINEAR` | `baseDelay × (attempt + 1)` |
| `FIXED` | `baseDelay` every time |

Optional `jitterFraction` (0.0–1.0) adds symmetric random jitter to reduce thundering herds.

```kotlin
SyncForge.android(context) {
    customize {
        backoffPolicy = SyncBackoffPolicy(
            strategy = SyncBackoffPolicy.Strategy.LINEAR,
            baseDelay = 2.seconds,
            maxDelay = 10.minutes,
            jitterFraction = 0.2,
        )
    }
}
```

When a push fails, [SyncEngine](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncEngine.kt) schedules the next attempt using `backoffPolicy.nextRetryAtMillis()` and stores it on the outbox row (`nextRetryAtMillis`).

## HTTP 429 and Retry-After

[KtorSyncTransport](../syncforge-network-ktor/src/commonMain/kotlin/dev/syncforge/network/KtorSyncTransport.kt) maps **HTTP 429** to `SyncError.Code.SERVER` (retryable). The `Retry-After` header (delay in seconds) is parsed into `SyncError.retryAfterMillis`.

The effective retry delay is `max(policyDelay, retryAfterMillis)` so the client never retries sooner than the server requests.

Pull failures with `Retry-After` also surface `retryAfterMillis`; [SyncManagerImpl](../syncforge/src/commonMain/kotlin/dev/syncforge/sync/SyncManagerImpl.kt) pauses further sync/push/pull cycles until that window elapses.

## Client-side throttle

Set `minSyncInterval` on `SyncConfig` (or `minSyncInterval` on [SyncForgeBuilder](../syncforge/src/commonMain/kotlin/dev/syncforge/SyncForgeBuilder.kt)) to enforce a minimum gap between sync cycles:

```kotlin
customize {
    minSyncInterval = 5.seconds
}
```

Back-to-back `sync()` / `push()` / `pull()` calls inside that window return `SyncResult.Success()` without hitting the network (outbox work is not discarded — it runs on the next allowed cycle).

## Defaults

| Setting | Default |
|---------|---------|
| `backoffPolicy` | Exponential, 1s base, 5m cap, no jitter |
| `minSyncInterval` | Disabled (`Duration.ZERO`) |
| `maxRetries` | 5 |

These defaults match pre-1.5 behavior for existing apps that do not customize policy.

## Related

- [BEST_PRACTICES.md](BEST_PRACTICES.md) — transient vs permanent failures
- [TRACING.md](TRACING.md) — `syncforge.retry` spans include scheduled delay
- [REST_API.md](REST_API.md) — server contract for push/pull