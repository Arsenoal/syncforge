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
import dev.syncforge.sample.notes.NoteEntity

@Composable
fun NotesScreen(viewModel: NotesViewModel) {
    val notes by viewModel.notes.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        Button(
            onClick = {
                viewModel.addNote(title, body)
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
                    onDelete = { viewModel.deleteNote(note) },
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteEntity,
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
            Text(
                text = syncStateLabel(note.syncState),
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