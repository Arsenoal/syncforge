package dev.syncforge.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.debug.SyncErrorBreakdown
import dev.syncforge.debug.SyncEvent
import dev.syncforge.debug.SyncHealth
import dev.syncforge.debug.SyncLatencyPercentiles
import dev.syncforge.model.SyncError
import dev.syncforge.model.SyncStatus
import java.text.DateFormat
import java.util.Date

/**
 * Structured SyncHealth dashboard — outbox, conflicts, latency, and error breakdown (1.5-03).
 */
@ExperimentalSyncForgeApi
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncHealthDashboard(
    health: SyncHealth,
    recentErrors: List<SyncEvent> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HealthStatusBanner(health = health)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = "Outbox",
                value = health.outboxDepth.toString(),
                subtitle = "${health.pendingOutboxCount} pending · peak ${health.maxOutboxDepth}",
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Conflicts",
                value = health.openConflictCount.toString(),
                subtitle = health.conflictRate?.let { "%.2f avg/pull".format(it) } ?: "No pull samples",
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = "Failed",
                value = health.failedOutboxCount.toString(),
                subtitle = "max retries ${health.maxRetries}",
                modifier = Modifier.weight(1f),
                highlight = health.failedOutboxCount > 0,
            )
            MetricTile(
                label = "Last sync",
                value = formatLastSynced(health.lastSyncedAtMillis),
                subtitle = "cursor ${health.pullCursorMillis}",
                modifier = Modifier.weight(1f),
            )
        }

        ErrorBreakdownSection(breakdown = health.errorBreakdown)

        if (recentErrors.isNotEmpty()) {
            RecentErrorsSection(errors = recentErrors)
        }

        HorizontalDivider()

        Text(text = "Latency", style = MaterialTheme.typography.titleSmall)
        LatencyBarRow(label = "Sync", percentiles = health.syncLatency)
        LatencyBarRow(label = "Push", percentiles = health.pushLatency)
        LatencyBarRow(label = "Pull", percentiles = health.pullLatency)
    }
}

@Composable
private fun HealthStatusBanner(health: SyncHealth) {
    val (title, detail, containerColor) = health.statusBanner()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (health.isOnline) "Network: online" else "Network: offline",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = if (highlight) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBreakdownSection(breakdown: SyncErrorBreakdown) {
    Text(text = "Error breakdown", style = MaterialTheme.typography.titleSmall)
    if (breakdown.totalFailures == 0) {
        Text(
            text = "No recorded failures in event log",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        breakdown.byCode.entries.sortedByDescending { it.value }.forEach { (code, count) ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("$code · $count") },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            )
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Total · ${breakdown.totalFailures}") },
        )
    }
}

@Composable
private fun RecentErrorsSection(errors: List<SyncEvent>) {
    Text(text = "Recent errors", style = MaterialTheme.typography.titleSmall)
    errors.forEach { event ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${event.type.name} · ${event.errorCode ?: SyncError.Code.UNKNOWN}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(text = event.summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LatencyBarRow(label: String, percentiles: SyncLatencyPercentiles) {
    if (percentiles.sampleCount == 0) {
        Text(
            text = "$label — no samples",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val maxMs = (percentiles.p99Millis ?: percentiles.p95Millis ?: percentiles.p50Millis ?: 1L)
        .coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = buildString {
                append(label)
                append(" · n=${percentiles.sampleCount}")
                percentiles.p50Millis?.let { append(" · p50 ${it}ms") }
                percentiles.p95Millis?.let { append(" · p95 ${it}ms") }
                percentiles.p99Millis?.let { append(" · p99 ${it}ms") }
            },
            style = MaterialTheme.typography.labelMedium,
        )
        LatencyBar("p50", percentiles.p50Millis, maxMs)
        LatencyBar("p95", percentiles.p95Millis, maxMs)
        LatencyBar("p99", percentiles.p99Millis, maxMs)
    }
}

@Composable
private fun LatencyBar(name: String, valueMillis: Long?, maxMs: Long) {
    if (valueMillis == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
        LinearProgressIndicator(
            progress = { (valueMillis.toFloat() / maxMs).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f),
        )
        Text(text = "${valueMillis}ms", style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatLastSynced(millis: Long?): String =
    millis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
        ?: "Never"

private data class StatusBanner(
    val title: String,
    val detail: String,
    val containerColor: Color,
)

@Composable
private fun SyncHealth.statusBanner(): StatusBanner {
    val syncing = isSyncing
    return when {
        !isOnline -> StatusBanner(
            title = "Offline",
            detail = "${pendingOutboxCount} change(s) queued",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        syncing -> StatusBanner(
            title = "Syncing",
            detail = status.toDebugLabel(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        status is SyncStatus.Error -> StatusBanner(
            title = "Sync error",
            detail = status.message,
            containerColor = MaterialTheme.colorScheme.errorContainer,
        )
        failedOutboxCount > 0 || openConflictCount > 0 -> StatusBanner(
            title = "Needs attention",
            detail = buildString {
                if (failedOutboxCount > 0) append("$failedOutboxCount failed · ")
                if (openConflictCount > 0) append("$openConflictCount open conflict(s)")
            }.trim(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        )
        else -> StatusBanner(
            title = "Healthy",
            detail = status.toDebugLabel(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}

private fun SyncStatus.toDebugLabel(): String = when (this) {
    SyncStatus.Idle -> "Idle"
    is SyncStatus.Syncing -> "Syncing (${phase.name})"
    is SyncStatus.Pending -> "Pending (outbox=$outboxCount, conflicts=$conflictCount)"
    is SyncStatus.Offline -> "Offline (queued=$outboxCount)"
    is SyncStatus.LastSynced -> "Last synced @ $timestampMillis"
    is SyncStatus.Error -> "Error: $message"
}