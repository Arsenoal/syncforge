package dev.syncforge.sample.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.syncforge.sample.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Temporary on-screen narration for README / demo recordings.
 * Debug builds only — remove or gate before 1.0 stable if no longer needed.
 */
object DemoActivityLog {
    private const val MAX_LINES = 24
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val _highlight = MutableStateFlow("Waiting for activity…")
    val highlight: StateFlow<String> = _highlight.asStateFlow()

    val enabled: Boolean get() = BuildConfig.DEBUG

    fun log(message: String, highlight: Boolean = false) {
        if (!enabled) return
        val line = "[${timeFormat.format(Date())}] $message"
        _messages.update { current -> (listOf(line) + current).take(MAX_LINES) }
        if (highlight) {
            _highlight.value = message
        }
    }

    fun setHighlight(message: String) {
        if (!enabled) return
        _highlight.value = message
    }

    fun clear() {
        _messages.value = emptyList()
        _highlight.value = "Log cleared"
    }
}

@Composable
fun DemoActivityLogPanel(
    onClearLocalData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!DemoActivityLog.enabled) return

    val messages by DemoActivityLog.messages.collectAsState()
    val highlight by DemoActivityLog.highlight.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("demo_activity_log"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Under the hood (demo)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Try: offline add → Sync → Clear local DB → Sync · " +
                    "Server edit/delete conflicts on Tasks · Notes+Tags multi-entity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = highlight,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("demo_highlight_field"),
                label = { Text("Now") },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = false,
                maxLines = 3,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(messages) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onClearLocalData,
                    modifier = Modifier.testTag("demo_clear_local_db"),
                ) {
                    Text("Clear local DB")
                }
                TextButton(onClick = { DemoActivityLog.clear() }) {
                    Text("Clear log")
                }
            }
        }
    }
}