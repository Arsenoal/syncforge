package dev.syncforge.sample.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: NotesViewModel) {
    val notes by viewModel.notes.collectAsState()
    val tags by viewModel.tags.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var selectedTagId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val tagLabels = tags.associate { it.id to it.label }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Notes use alwaysRemote() — server copy wins on conflict. tagId links to Tags (separate sync).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_note_title_input"),
            label = { Text("Title") },
            singleLine = true,
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_note_body_input"),
            label = { Text("Body (optional)") },
        )
        ExposedDropdownMenuBox(
            expanded = tagMenuExpanded,
            onExpandedChange = { tagMenuExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            val selectedLabel = selectedTagId?.let { tagLabels[it] } ?: "No tag"
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .testTag("note_tag_dropdown"),
                label = { Text("Tag (optional)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagMenuExpanded) },
            )
            ExposedDropdownMenu(
                expanded = tagMenuExpanded,
                onDismissRequest = { tagMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("No tag") },
                    onClick = {
                        selectedTagId = null
                        tagMenuExpanded = false
                    },
                )
                tags.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag.label) },
                        onClick = {
                            selectedTagId = tag.id
                            tagMenuExpanded = false
                        },
                        modifier = Modifier.testTag("tag_option_${tag.label}"),
                    )
                }
            }
        }
        Button(
            onClick = {
                viewModel.addNote(title, body, selectedTagId)
                title = ""
                body = ""
            },
            modifier = Modifier.testTag("add_note_button"),
        ) {
            Text("Add note")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                NoteRow(
                    note = note,
                    tagLabel = note.tagId?.let { tagLabels[it] },
                    onDelete = { viewModel.deleteNote(note) },
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
    tagLabel: String?,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = note.title, style = MaterialTheme.typography.bodyLarge)
            if (note.body.isNotBlank()) {
                Text(text = note.body, style = MaterialTheme.typography.bodyMedium)
            }
            if (tagLabel != null) {
                Text(
                    text = "Tag: $tagLabel",
                    modifier = Modifier.testTag("note_tag_label_${note.title}"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = syncStateLabel(note.syncState),
                modifier = Modifier.testTag("row_sync_state_${note.title}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_note_${note.id}"),
        ) {
            Text("Delete")
        }
    }
}

private fun syncStateLabel(state: SyncState): String = when (state) {
    SyncState.SYNCED -> "Synced"
    SyncState.PENDING -> "Pending"
    SyncState.CONFLICT -> "Conflict"
    SyncState.FAILED -> "Failed"
}