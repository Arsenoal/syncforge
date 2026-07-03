import XCTest

/// Shared XCUITest helpers for ios-sample against :mock-server on 127.0.0.1:8080.
class SampleUITestBase: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false

        // Gradle :iosE2e already waits for mock-server health before xcodebuild.
        resetMockServer()

        app = XCUIApplication()
        app.launchEnvironment["MOCK_SERVER_BASE_URL"] = Self.mockServerBaseUrl
        app.launchEnvironment["E2E_TESTING"] = "1"
        app.launchArguments += ["-E2E_TESTING", "-UIViewAnimationEnabled", "NO"]
        // launchWithoutWaitingForQuiescence() is macOS-only; weak-linked KMP keeps iOS launch fast.
        app.launch()
        waitForAppReady()
        waitForKotlinBridge()
    }

    override func tearDownWithError() throws {
        if app != nil {
            app.terminate()
            // Let Kotlin/Native runtime shut down before the next test launches.
            Thread.sleep(forTimeInterval: 1.0)
        }
        try super.tearDownWithError()
    }

    func waitForAppReady(file: StaticString = #filePath, line: UInt = #line) {
        let markers: [XCUIElement] = [
            element(identifier: "sync_button", fallbackLabels: ["Sync"]),
            element(identifier: "new_task_input", fallbackLabels: ["New task"], type: .textField),
            app.otherElements["syncforge_tasks_root"],
            app.staticTexts["Idle"],
            app.staticTexts["No tasks yet"],
        ]
        let deadline = Date().addingTimeInterval(45)
        while Date() < deadline {
            if markers.contains(where: { $0.waitForExistence(timeout: 0.5) }) {
                return
            }
        }
        XCTFail(
            "Tasks screen did not appear. Debug:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    /// Kotlin frameworks load lazily after SwiftUI is visible — allow time on CI simulators.
    func waitForKotlinBridge(
        timeout: TimeInterval = 120,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let ready = app.descendants(matching: .any)["e2e_kotlin_ready"]
        let syncButton = element(identifier: "sync_button", fallbackLabels: ["Sync"])
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if ready.waitForExistence(timeout: 0.5) {
                return
            }
            if syncButton.waitForExistence(timeout: 0.5) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail(
            "Kotlin bridge did not finish loading within \(timeout)s. Debug:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    func navigateToTasks(file: StaticString = #filePath, line: UInt = #line) {
        tapTab("Tasks", identifier: "nav_tasks", file: file, line: line)
        XCTAssertTrue(
            textField(identifier: "new_task_input", fallbackLabel: "New task").waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func navigateToNotes(file: StaticString = #filePath, line: UInt = #line) {
        tapTab("Notes", identifier: "nav_notes", file: file, line: line)
        XCTAssertTrue(
            element(identifier: "add_note_button", fallbackLabels: ["Add note"]).waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func navigateToTags(file: StaticString = #filePath, line: UInt = #line) {
        tapTab("Tags", identifier: "nav_tags", file: file, line: line)
        XCTAssertTrue(
            element(identifier: "new_tag_input", fallbackLabels: ["New tag"], type: .textField).waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func addTask(_ title: String, file: StaticString = #filePath, line: UInt = #line) {
        navigateToTasks()
        let field = textField(identifier: "new_task_input", fallbackLabel: "New task")
        field.tap()
        field.typeText(title)
        dismissKeyboardIfNeeded()
        element(identifier: "add_task_button", fallbackLabels: ["Add"]).tap()
        XCTAssertTrue(
            app.staticTexts[title].waitForExistence(timeout: 15),
            "Task row did not appear",
            file: file,
            line: line
        )
    }

    func addNote(title: String, body: String = "", file: StaticString = #filePath, line: UInt = #line) {
        navigateToNotes()
        let titleField = textField(identifier: "new_note_title_input", fallbackLabel: "Title")
        titleField.tap()
        titleField.typeText(title)
        if !body.isEmpty {
            let bodyField = textField(identifier: "new_note_body_input", fallbackLabel: "Body (optional)")
            bodyField.tap()
            bodyField.typeText(body)
        }
        dismissKeyboardIfNeeded()
        element(identifier: "add_note_button", fallbackLabels: ["Add note"]).tap()
        XCTAssertTrue(
            app.staticTexts[title].waitForExistence(timeout: 15),
            "Note row did not appear",
            file: file,
            line: line
        )
    }

    func addTag(_ label: String, file: StaticString = #filePath, line: UInt = #line) {
        navigateToTags()
        let field = textField(identifier: "new_tag_input", fallbackLabel: "New tag")
        field.tap()
        field.typeText(label)
        dismissKeyboardIfNeeded()
        element(identifier: "add_tag_button", fallbackLabels: ["Add"]).tap()
        XCTAssertTrue(
            app.staticTexts[label].waitForExistence(timeout: 15),
            "Tag row did not appear",
            file: file,
            line: line
        )
    }

    func tapSync(file: StaticString = #filePath, line: UInt = #line) {
        dismissKeyboardIfNeeded()
        let syncButton = element(identifier: "sync_button", fallbackLabels: ["Sync", "Syncing…"])
        XCTAssertTrue(
            syncButton.waitForExistence(timeout: 15),
            "Sync button not found. Debug:\n\(app.debugDescription)",
            file: file,
            line: line
        )
        if syncButton.isHittable {
            syncButton.tap()
        } else {
            syncButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
    }

    func waitForSyncToFinish(timeout: TimeInterval = 45, file: StaticString = #filePath, line: UInt = #line) {
        let syncingPredicate = NSPredicate(format: "label CONTAINS[c] %@", "Syncing")
        let syncing = app.staticTexts.containing(syncingPredicate).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if !syncing.exists {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail("Sync did not finish within \(timeout)s", file: file, line: line)
    }

    func waitForAnyStatus(
        _ options: [String],
        timeout: TimeInterval = 30,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let status = app.descendants(matching: .any)["sync_status_label"]
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let label = status.label
            if options.contains(where: { label.localizedCaseInsensitiveContains($0) }) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail("Status did not match \(options); last label: \(status.label)", file: file, line: line)
    }

    func waitForRowSyncState(
        itemTitle: String,
        stateLabel: String,
        timeout: TimeInterval = 45,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let hasTitle = app.staticTexts[itemTitle].exists
            let hasState = app.staticTexts[stateLabel].exists
            if hasTitle && hasState {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail(
            "Row \(itemTitle) did not reach state \(stateLabel) within \(timeout)s",
            file: file,
            line: line
        )
    }

    func uniqueTitle(_ prefix: String) -> String {
        "\(prefix) \(Int(Date().timeIntervalSince1970 * 1000))"
    }

    private func tapTab(
        _ label: String,
        identifier: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let tabButton = app.tabBars.buttons[label]
        XCTAssertTrue(
            tabButton.waitForExistence(timeout: 10),
            "Tab \(label) not found (identifier: \(identifier))",
            file: file,
            line: line
        )
        tabButton.tap()
    }

    private func textField(identifier: String, fallbackLabel: String) -> XCUIElement {
        let byId = element(identifier: identifier, fallbackLabels: [fallbackLabel], type: .textField)
        if byId.exists {
            return byId
        }
        return app.textFields[fallbackLabel]
    }

    private func element(
        identifier: String,
        fallbackLabels: [String] = [],
        type: XCUIElement.ElementType = .any
    ) -> XCUIElement {
        let byId = app.descendants(matching: type)[identifier]
        if byId.exists {
            return byId
        }
        for label in fallbackLabels {
            let byLabel = app.descendants(matching: type)[label]
            if byLabel.exists {
                return byLabel
            }
        }
        return byId
    }

    private func dismissKeyboardIfNeeded() {
        guard app.keyboards.count > 0 else { return }
        for key in ["Return", "Done", "done", "Go"] {
            let button = app.keyboards.buttons[key]
            if button.exists {
                button.tap()
                return
            }
        }
    }

    private func resetMockServer() {
        guard let url = URL(string: "\(Self.mockServerBaseUrl)/dev/reset") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10

        for _ in 0..<3 {
            let semaphore = DispatchSemaphore(value: 0)
            var statusCode = 0
            URLSession.shared.dataTask(with: request) { _, response, _ in
                if let http = response as? HTTPURLResponse {
                    statusCode = http.statusCode
                }
                semaphore.signal()
            }.resume()
            if semaphore.wait(timeout: .now() + 12) == .success, (200...299).contains(statusCode) {
                return
            }
            Thread.sleep(forTimeInterval: 0.5)
        }
    }

    private static var mockServerBaseUrl: String {
        ProcessInfo.processInfo.environment["MOCK_SERVER_BASE_URL"] ?? "http://127.0.0.1:8080"
    }
}