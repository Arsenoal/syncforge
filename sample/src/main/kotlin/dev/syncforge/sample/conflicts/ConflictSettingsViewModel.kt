package dev.syncforge.sample.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.sync.SyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConflictSettingsViewModel(
    private val syncManager: SyncManager,
    private val policyStore: SampleConflictPolicyStore,
) : ViewModel() {

    val kinds: StateFlow<SampleEntityConflictKinds> = policyStore.kindsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SampleEntityConflictKinds.Default,
        )

    fun setEntityKind(entityType: String, kind: ConflictStrategyKind) {
        viewModelScope.launch {
            val updated = policyStore.currentKinds().withKind(entityType, kind)
            policyStore.save(updated)
            syncManager.updateConflictPolicy(conflictPolicyFromSampleKinds(updated))
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            policyStore.resetToDefaults()
            syncManager.updateConflictPolicy(
                conflictPolicyFromSampleKinds(SampleEntityConflictKinds.Default),
            )
        }
    }
}