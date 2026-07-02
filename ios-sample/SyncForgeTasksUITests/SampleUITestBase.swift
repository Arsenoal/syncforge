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
        app.launchArguments += ["-UIViewAnimationEnabled", "NO"]
        app.launch()
        waitForAppReady()
    }

    override func tearDownWithError() throws {
        if app != nil {
            app.terminate()
        }
        try super.tearDownWithError()
    }

    func waitForAppReady(file: StaticString = #filePath, line: UInt = #line) {
        let readyMarkers = [
            app.textFields["new_task_input"],
            app.textFields["New task"],
            app.staticTexts["No tasks yet"],
            app.buttons["sync_button"],
            app.tabBars.buttons["Tasks"],
        ]
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if readyMarkers.contains(where: { $0.exists }) {
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTFail(
            "Tasks screen did not appear. Debug:\n\(app.debugDescription)",
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
            app.buttons["add_note_button"].waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func navigateToTags(file: StaticString = #filePath, line: UInt = #line) {
        tapTab("Tags", identifier: "nav_tags", file: file, line: line)
        XCTAssertTrue(
            app.textFields["new_tag_input"].waitForExistence(timeout: 15),
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
        app.buttons["add_task_button"].tap()
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
        app.buttons["add_note_button"].tap()
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
        app.buttons["add_tag_button"].tap()
        XCTAssertTrue(
            app.staticTexts[label].waitForExistence(timeout: 15),
            "Tag row did not appear",
            file: file,
            line: line
        )
    }

    func tapSync(file: StaticString = #filePath, line: UInt = #line) {
        let syncButton = app.buttons["sync_button"]
        XCTAssertTrue(syncButton.waitForExistence(timeout: 5), file: file, line: line)
        syncButton.tap()
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
        if tabButton.waitForExistence(timeout: 3) {
            tabButton.tap()
            return
        }
        let fallback = app.buttons[identifier]
        XCTAssertTrue(fallback.waitForExistence(timeout: 10), "Tab \(label) not found", file: file, line: line)
        fallback.tap()
    }

    private func textField(identifier: String, fallbackLabel: String) -> XCUIElement {
        let byId = app.textFields[identifier]
        if byId.exists {
            return byId
        }
        return app.textFields[fallbackLabel]
    }

    private func dismissKeyboardIfNeeded() {
        let returnKey = app.keyboards.buttons["Return"]
        if returnKey.exists {
            returnKey.tap()
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