import XCTest

/// Multi-entity UI smoke tests — mirrors Android [MultiEntityE2ETest].
final class MultiEntityUITests: SampleUITestBase {

    func testAddTaskNoteAndTag_singleSync_allEntitiesReachSyncedState() throws {
        let taskTitle = uniqueTitle("UI Multi Task")
        let noteTitle = uniqueTitle("UI Multi Note")
        let tagLabel = uniqueTitle("UI Multi Tag")

        addTask(taskTitle)
        addNote(title: noteTitle, body: "Created during multi-entity UI test")
        addTag(tagLabel)

        tapSync()
        waitForSyncToFinish()
        waitForAnyStatus(["Up to date", "Synced", "Last synced"])

        navigateToNotes()
        waitForRowSyncState(itemTitle: noteTitle, stateLabel: "Synced")

        navigateToTags()
        waitForRowSyncState(itemTitle: tagLabel, stateLabel: "Synced")

        navigateToTasks()
        waitForRowSyncState(itemTitle: taskTitle, stateLabel: "Synced")
    }

    func testAddTaskAndNote_singleSync_bothEntitiesReachSyncedState() throws {
        let taskTitle = uniqueTitle("UI Task")
        let noteTitle = uniqueTitle("UI Note")

        addTask(taskTitle)
        addNote(title: noteTitle)

        tapSync()
        waitForSyncToFinish()
        waitForAnyStatus(["Up to date", "Synced", "Last synced"])

        navigateToNotes()
        waitForRowSyncState(itemTitle: noteTitle, stateLabel: "Synced")

        navigateToTasks()
        waitForRowSyncState(itemTitle: taskTitle, stateLabel: "Synced")
    }
}