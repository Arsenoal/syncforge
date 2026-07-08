package dev.syncforge.sample.conflicts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.sample.notes.NoteEntity
import dev.syncforge.sample.tags.TagEntity
import dev.syncforge.sample.tasks.TaskEntity

@Composable
fun ConflictSettingsScreen(
    viewModel: ConflictSettingsViewModel,
) {
    val kinds by viewModel.kinds.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("conflict_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Conflict policy",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Changes apply to the next pull — open deferred conflicts are unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EntityConflictKindRow(
            entityLabel = "Notes",
            entityType = NoteEntity.ENTITY_TYPE,
            selected = kinds.notes,
            options = ConflictSettingsCatalog.notesAndTags,
            onSelect = viewModel::setEntityKind,
        )
        EntityConflictKindRow(
            entityLabel = "Tags",
            entityType = TagEntity.ENTITY_TYPE,
            selected = kinds.tags,
            options = ConflictSettingsCatalog.notesAndTags,
            onSelect = viewModel::setEntityKind,
        )
        EntityConflictKindRow(
            entityLabel = "Tasks",
            entityType = TaskEntity.ENTITY_TYPE,
            selected = kinds.tasks,
            options = ConflictSettingsCatalog.tasks,
            onSelect = viewModel::setEntityKind,
        )
        Text(
            text = "Merge and CRDT strategies require static DSL blocks — configure in code, not here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = viewModel::resetToDefaults,
            modifier = Modifier.testTag("conflict_settings_reset"),
        ) {
            Text("Reset to sample defaults")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityConflictKindRow(
    entityLabel: String,
    entityType: String,
    selected: ConflictStrategyKind,
    options: List<ConflictStrategyKind>,
    onSelect: (String, ConflictStrategyKind) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.displayLabel(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("conflict_kind_dropdown_$entityType"),
            label = { Text("$entityLabel strategy") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.displayLabel()) },
                    onClick = {
                        expanded = false
                        onSelect(entityType, kind)
                    },
                    modifier = Modifier.testTag("conflict_kind_option_${entityType}_${kind.name}"),
                )
            }
        }
    }
}