package dev.syncforge.sample.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.syncforge.model.SyncState

@Composable
fun TagsScreen(viewModel: TagsViewModel) {
    val tags by viewModel.tags.collectAsState()
    val devMessage by viewModel.devMessage.collectAsState()
    var label by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Tags sync with last-write-wins — create before linking notes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("new_tag_input"),
                label = { Text("New tag") },
                singleLine = true,
            )
            Button(
                onClick = {
                    viewModel.addTag(label)
                    label = ""
                },
                modifier = Modifier.testTag("add_tag_button"),
            ) {
                Text("Add")
            }
        }

        devMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tags, key = { it.id }) { tag ->
                TagRow(
                    tag = tag,
                    onLocalEdit = { viewModel.applyLocalEdit(tag) },
                    onSimulateServerEdit = { viewModel.simulateServerEdit(tag) },
                    onDelete = { viewModel.deleteTag(tag) },
                )
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: TagEntity,
    onLocalEdit: () -> Unit,
    onSimulateServerEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.label,
                modifier = Modifier.testTag("tag_label_${tag.label}"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when (tag.syncState) {
                    SyncState.SYNCED -> "Synced"
                    SyncState.PENDING -> "Pending"
                    SyncState.CONFLICT -> "Conflict"
                    SyncState.FAILED -> "Failed"
                },
                modifier = Modifier.testTag("row_sync_state_${tag.label}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            if (tag.syncState == SyncState.SYNCED) {
                TextButton(
                    onClick = onLocalEdit,
                    modifier = Modifier.testTag("local_edit_${tag.label}"),
                ) { Text("Local edit") }
                TextButton(
                    onClick = onSimulateServerEdit,
                    modifier = Modifier.testTag("server_edit_${tag.label}"),
                ) { Text("Server edit") }
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_tag_${tag.id}"),
            ) {
                Text("Delete")
            }
        }
    }
}