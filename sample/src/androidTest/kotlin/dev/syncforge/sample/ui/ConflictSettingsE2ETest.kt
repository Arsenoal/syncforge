package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.syncforge.conflict.ConflictStrategyKind
import dev.syncforge.sample.tags.TagEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 1.2-10 — runtime [dev.syncforge.sync.SyncManager.updateConflictPolicy] via settings UI. */
@RunWith(AndroidJUnit4::class)
class ConflictSettingsE2ETest : SampleE2ETestBase() {

    @Before
    fun setUp() {
        prepareDevice()
    }

    @Test
    fun runtimePolicy_tagsDeferToUser_defersConcurrentEdit() {
        navigateToConflictSettings()
        selectConflictKind(TagEntity.ENTITY_TYPE, ConflictStrategyKind.DEFER_TO_USER)

        val baseLabel = uniqueTitle("E2E Runtime Defer")
        val localLabel = tagLocalLabel(baseLabel)
        val serverLabel = tagServerLabel(baseLabel)

        addTag(baseLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(baseLabel, "Synced", timeoutMillis = 45_000)

        tapTagLocalEdit(baseLabel)
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 30_000)

        simulateServerTagEdit(localLabel, serverLabel)

        syncAndWaitForIdle()
        waitForRowSyncState(localLabel, "Conflict", timeoutMillis = 45_000)
    }
}