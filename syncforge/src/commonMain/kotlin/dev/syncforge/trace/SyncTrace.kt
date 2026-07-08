package dev.syncforge.trace

import dev.syncforge.model.SyncResult

/**
 * Inline span helpers — when [SyncTracer.isEnabled] is false, no span objects are allocated.
 */
internal inline fun <T> SyncTracer.runSpan(
    name: SyncSpanName,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: (SyncSpan) -> T,
): T {
    if (!isEnabled) return block(NoOpSyncSpan)
    val span = startSpan(name, attributes)
    return try {
        val result = block(span)
        span.end(SyncSpanStatus.OK)
        result
    } catch (e: Throwable) {
        span.recordException(e)
        span.end(SyncSpanStatus.ERROR)
        throw e
    }
}

internal inline fun <T> SyncTracer.runSpan(
    name: SyncSpanName,
    attributes: Map<String, String> = emptyMap(),
    crossinline statusFor: (T) -> SyncSpanStatus,
    crossinline block: (SyncSpan) -> T,
): T {
    if (!isEnabled) return block(NoOpSyncSpan)
    val span = startSpan(name, attributes)
    return try {
        val result = block(span)
        span.end(statusFor(result))
        result
    } catch (e: Throwable) {
        span.recordException(e)
        span.end(SyncSpanStatus.ERROR)
        throw e
    }
}

internal suspend inline fun <T> SyncTracer.runSuspendSpan(
    name: SyncSpanName,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: suspend (SyncSpan) -> T,
): T {
    if (!isEnabled) return block(NoOpSyncSpan)
    val span = startSpan(name, attributes)
    return try {
        val result = block(span)
        span.end(SyncSpanStatus.OK)
        result
    } catch (e: Throwable) {
        span.recordException(e)
        span.end(SyncSpanStatus.ERROR)
        throw e
    }
}

internal suspend inline fun <T> SyncTracer.runSuspendSpan(
    name: SyncSpanName,
    attributes: Map<String, String> = emptyMap(),
    crossinline statusFor: (T) -> SyncSpanStatus,
    crossinline block: suspend (SyncSpan) -> T,
): T {
    if (!isEnabled) return block(NoOpSyncSpan)
    val span = startSpan(name, attributes)
    return try {
        val result = block(span)
        span.end(statusFor(result))
        result
    } catch (e: Throwable) {
        span.recordException(e)
        span.end(SyncSpanStatus.ERROR)
        throw e
    }
}

internal fun SyncSpan.recordSyncResult(result: SyncResult) {
    when (result) {
        is SyncResult.Success -> {
            setAttribute(SyncTraceAttributes.ACKNOWLEDGED_COUNT, result.pushed.toLong())
            setAttribute(SyncTraceAttributes.PULLED_COUNT, result.pulled.toLong())
            setAttribute(SyncTraceAttributes.CONFLICTS_RESOLVED, result.conflictsResolved.toLong())
        }
        is SyncResult.Partial -> {
            recordSyncResult(result.success)
            result.errors.firstOrNull()?.code?.name?.let { setAttribute(SyncTraceAttributes.ERROR_CODE, it) }
        }
        is SyncResult.Failure -> {
            setAttribute(SyncTraceAttributes.ERROR_CODE, result.error.code.name)
        }
    }
}

internal fun syncSpanStatusFor(result: SyncResult): SyncSpanStatus =
    when (result) {
        is SyncResult.Failure -> SyncSpanStatus.ERROR
        else -> SyncSpanStatus.OK
    }