package dev.syncforge.sample.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §10 conflict-strategy E2E — validates [:sample] [SampleConflictPolicies] on one emulator
 * with mock-server standing in for a second device.
 *
 * §10.6 multi-entity isolation — shared [dev.syncforge.sync.SyncManager], independent
 * per-entity policies (tasks gitLike, notes alwaysRemote, tags lastWriteWins).
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

    @Test
    fun notes_alwaysRemote_acceptsServerOnConcurrentBodyEdit() {
        val title = uniqueTitle("E2E Note Remote")
        val baseBody = "Original body"
        val localBody = noteLocalBody(baseBody)
        val serverBody = noteServerBody(baseBody)

        addNote(title, baseBody)
        syncAndWaitForIdle()
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)
        waitForNoteBody(title, baseBody)

        tapNoteLocalEdit(title)
        waitForRowSyncState(title, "Pending", timeoutMillis = 45_000)
        waitForNoteBody(title, localBody)

        simulateServerNoteEdit(title, serverBody)

        syncAndWaitForIdle()
        waitForNoteBody(title, serverBody, timeoutMillis = 45_000)
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun notes_alwaysRemote_localNewerStillAcceptsServer() {
        val title = uniqueTitle("E2E Note Local Newer")
        val baseBody = "Baseline body"
        val localBody = noteLocalBody(baseBody)
        val serverBody = noteServerBody(baseBody)

        addNote(title, baseBody)
        syncAndWaitForIdle()
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)

        val serverUpdatedAtMillis = simulateServerNoteEdit(title, serverBody)
        applyNoteLocalBodyEditNewerThan(title, localBody, serverUpdatedAtMillis)
        waitForRowSyncState(title, "Pending", timeoutMillis = 15_000)
        waitForNoteBody(title, localBody)

        syncAndWaitForIdle()
        waitForNoteBody(title, serverBody, timeoutMillis = 45_000)
        waitForRowSyncState(title, "Synced", timeoutMillis = 45_000)
    }

    @Test
    fun multiEntity_taskAutoMerge_noteStillSyncs() {
        val taskTitle = uniqueTitle("E2E Iso Merge Task")
        val noteTitle = uniqueTitle("E2E Iso Merge Note")

        addTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(taskTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        toggleCheckboxForTask(taskTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(serverEditedTitle(taskTitle), "Synced", timeoutMillis = 45_000)

        addNote(noteTitle, body = "Isolated while task auto-merges")
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 60_000)

        navigateToTasks()
        waitForRowSyncState(serverEditedTitle(taskTitle), "Synced", timeoutMillis = 30_000)
    }

    @Test
    fun multiEntity_taskDefer_noteStillSyncs() {
        val baseTitle = uniqueTitle("E2E Iso Defer Task")
        val localTitle = taskLocalTitle(baseTitle)
        val noteTitle = uniqueTitle("E2E Iso Defer Note")

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)
        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)

        addNote(noteTitle, body = "Should sync while task stays in conflict")
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 60_000)

        navigateToTasks()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 30_000)
    }

    @Test
    fun multiEntity_taskDefer_tagStillSyncs() {
        val baseTitle = uniqueTitle("E2E Iso Defer Tag Task")
        val localTitle = taskLocalTitle(baseTitle)
        val tagLabel = uniqueTitle("E2E Iso Defer Tag")

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)
        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)

        addTag(tagLabel)
        syncAndWaitForIdle()
        navigateToTags()
        waitForRowSyncState(tagLabel, "Synced", timeoutMillis = 60_000)

        navigateToTasks()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 30_000)
    }

    @Test
    fun multiEntity_taskDefer_noteAlwaysRemoteStillSyncs() {
        val baseTitle = uniqueTitle("E2E Iso Defer Note Task")
        val localTitle = taskLocalTitle(baseTitle)
        val noteTitle = uniqueTitle("E2E Iso Defer Note Remote")
        val baseBody = "Body under task conflict"
        val serverBody = noteServerBody(baseBody)

        addTask(baseTitle)
        syncAndWaitForIdle()
        waitForRowSyncState(baseTitle, "Synced", timeoutMillis = 45_000)

        tapServerEdit()
        waitForTextContains("Server updated")
        tapTaskLocalEdit(baseTitle)
        waitForRowSyncState(localTitle, "Pending", timeoutMillis = 15_000)
        syncAndWaitForIdle()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 45_000)

        addNote(noteTitle, baseBody)
        syncAndWaitForIdle()
        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 45_000)

        tapNoteLocalEdit(noteTitle)
        waitForRowSyncState(noteTitle, "Pending", timeoutMillis = 45_000)
        simulateServerNoteEdit(noteTitle, serverBody)

        syncAndWaitForIdle()
        waitForNoteBody(noteTitle, serverBody, timeoutMillis = 45_000)
        waitForRowSyncState(noteTitle, "Synced", timeoutMillis = 45_000)

        navigateToTasks()
        waitForRowSyncState(localTitle, "Conflict — tap Resolve", timeoutMillis = 30_000)
    }
}