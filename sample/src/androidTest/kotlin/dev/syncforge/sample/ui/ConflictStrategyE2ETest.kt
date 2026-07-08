package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §10 conflict-strategy E2E — validates [:sample] [SampleConflictPolicies] on one emulator
 * with mock-server standing in for a second device.
 */
@RunWith(AndroidJUnit4::class)
class ConflictStrategyE2ETest : SampleE2ETestBase() {

    @Before
    fun setUp() {
        prepareDevice()
    }

    @Test
    fun tags_lww_remoteNewerWinsOnConcurrentEdit() {
        val baseLabel = uniqueTitle("E2E LWW Remote")
        val localLabel = tagLocalLabel(baseLabel)
        val serverLabel = tagServerLabel(baseLabel)

        addTag(baseLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(baseLabel, "Synced", timeoutMillis = 45_000)

        tapTagLocalEdit(baseLabel)
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 15_000)

        simulateServerTagEdit(localLabel, serverLabel)

        syncAndWaitForIdle()
        waitForRowSyncState(serverLabel, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun tags_lww_localNewerWinsOnConcurrentEdit() {
        val baseLabel = uniqueTitle("E2E LWW Local")
        val localLabel = tagLocalLabel(baseLabel)

        addTag(baseLabel)
        syncAndWaitForIdle()
        waitForRowSyncState(baseLabel, "Synced", timeoutMillis = 45_000)

        val serverUpdatedAtMillis = simulateServerTagEdit(baseLabel, tagServerLabel(baseLabel))
        applyTagLocalEditNewerThan(baseLabel, serverUpdatedAtMillis)
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 15_000)

        syncAndWaitForIdle()
        // LWW KeepLocal — newer local timestamp wins but the row stays pending (push still OCC-stale).
        waitForRowSyncState(localLabel, "Pending", timeoutMillis = 45_000)
    }

    @Test
    fun tasks_gitLike_unmergeableTitleClash_defersToUser() {
        val baseTitle = uniqueTitle("E2E Git Defer")
        val localTitle = taskLocalTitle(baseTitle)

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)

        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)
    }

    @Test
    fun tasks_gitLike_unmergeableTitleClash_resolveAcceptRemote() {
        val baseTitle = uniqueTitle("E2E Git Remote")
        val localTitle = taskLocalTitle(baseTitle)
        val serverTitle = serverEditedTitle(baseTitle)

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)

        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)

        resolveConflictAcceptRemote()
        waitForRowSyncState(serverTitle, "Synced", timeoutMillis = 45_000)
        waitForTextGone("Conflict — tap Resolve", timeoutMillis = 15_000)
    }

    @Test
    fun tasks_gitLike_unmergeableTitleClash_resolveKeepLocal() {
        val baseTitle = uniqueTitle("E2E Git Local")
        val localTitle = taskLocalTitle(baseTitle)

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)

        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)

        resolveConflictKeepLocal()
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 45_000)
        waitForTextGone("Conflict — tap Resolve", timeoutMillis = 15_000)
    }
}