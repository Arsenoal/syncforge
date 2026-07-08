package dev.syncforge.sample.conflicts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.sample.notes.NoteEntity
import dev.syncforge.sample.tags.TagEntity
import dev.syncforge.sample.tasks.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sampleConflictPolicyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sample_conflict_policy",
)

class SampleConflictPolicyStore(
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context.applicationContext.sampleConflictPolicyDataStore)

    val kindsFlow: Flow<SampleEntityConflictKinds> =
        dataStore.data.map { prefs -> prefs.toKinds() }

    suspend fun currentKinds(): SampleEntityConflictKinds = kindsFlow.first()

    suspend fun save(kinds: SampleEntityConflictKinds) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTES] = kinds.notes.name
            prefs[KEY_TAGS] = kinds.tags.name
            prefs[KEY_TASKS] = kinds.tasks.name
        }
    }

    suspend fun resetToDefaults() {
        save(SampleEntityConflictKinds.Default)
    }

    private fun Preferences.toKinds(): SampleEntityConflictKinds {
        val defaults = SampleEntityConflictKinds.Default
        return SampleEntityConflictKinds(
            notes = parseKind(this[KEY_NOTES], defaults.notes),
            tags = parseKind(this[KEY_TAGS], defaults.tags),
            tasks = parseKind(this[KEY_TASKS], defaults.tasks),
        )
    }

    private fun parseKind(raw: String?, fallback: ConflictStrategyKind): ConflictStrategyKind =
        runCatching { ConflictStrategyKind.valueOf(raw!!) }.getOrDefault(fallback)

    companion object {
        private val KEY_NOTES = stringPreferencesKey("kind_${NoteEntity.ENTITY_TYPE}")
        private val KEY_TAGS = stringPreferencesKey("kind_${TagEntity.ENTITY_TYPE}")
        private val KEY_TASKS = stringPreferencesKey("kind_${TaskEntity.ENTITY_TYPE}")
    }
}