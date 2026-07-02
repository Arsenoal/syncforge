import XCTest

/// Shared XCUITest helpers for ios-sample against :mock-server on localhost:8080.
class SampleUITestBase: XCTestCase {

    var app: XCUIApplication!

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false

        guard Self.isMockServerHealthy() else {
            throw XCTSkip("Mock server must be running on localhost:8080 (./scripts/run-ios-e2e.sh)")
        }

        app = XCUIApplication()
        app.launch()
        waitForAppReady()
    }

    func waitForAppReady(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(
            app.textFields["new_task_input"].waitForExistence(timeout: 20),
            "Tasks screen did not appear",
            file: file,
            line: line
        )
        XCTAssertTrue(
            app.buttons["sync_button"].waitForExistence(timeout: 5),
            "Sync button did not appear",
            file: file,
            line: line
        )
    }

    func navigateToTasks(file: StaticString = #filePath, line: UInt = #line) {
        app.tabBars.buttons["Tasks"].tap()
        XCTAssertTrue(
            app.textFields["new_task_input"].waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func navigateToNotes(file: StaticString = #filePath, line: UInt = #line) {
        app.tabBars.buttons["Notes"].tap()
        XCTAssertTrue(
            app.buttons["add_note_button"].waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func navigateToTags(file: StaticString = #filePath, line: UInt = #line) {
        app.tabBars.buttons["Tags"].tap()
        XCTAssertTrue(
            app.textFields["new_tag_input"].waitForExistence(timeout: 15),
            file: file,
            line: line
        )
    }

    func addTask(_ title: String, file: StaticString = #filePath, line: UInt = #line) {
        navigateToTasks()
        let field = app.textFields["new_task_input"]
        field.tap()
        field.typeText(title)
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
        let titleField = app.textFields["new_note_title_input"]
        titleField.tap()
        titleField.typeText(title)
        if !body.isEmpty {
            let bodyField = app.textFields["new_note_body_input"]
            bodyField.tap()
            bodyField.typeText(body)
        }
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
        let field = app.textFields["new_tag_input"]
        field.tap()
        field.typeText(label)
        app.buttons["add_tag_button"].tap()
        XCTAssertTrue(
            app.staticTexts[label].waitForExistence(timeout: 15),
            "Tag row did not appear",
            file: file,
            line: line
        )
    }

    func tapSync(file: StaticString = #filePath, line: UInt = #line) {
        app.buttons["sync_button"].tap()
    }

    func waitForSyncToFinish(timeout: TimeInterval = 30, file: StaticString = #filePath, line: UInt = #line) {
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
        timeout: TimeInterval = 15,
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
        timeout: TimeInterval = 30,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertTrue(
            app.staticTexts[itemTitle].waitForExistence(timeout: timeout),
            "Item \(itemTitle) not found",
            file: file,
            line: line
        )
        XCTAssertTrue(
            app.staticTexts[stateLabel].waitForExistence(timeout: timeout),
            "State \(stateLabel) not found for \(itemTitle)",
            file: file,
            line: line
        )
    }

    func uniqueTitle(_ prefix: String) -> String {
        "\(prefix) \(Int(Date().timeIntervalSince1970 * 1000))"
    }

    private static func isMockServerHealthy() -> Bool {
        let urlString = ProcessInfo.processInfo.environment["MOCK_SERVER_HEALTH_URL"]
            ?? "http://127.0.0.1:8080/health"
        guard let url = URL(string: urlString) else { return false }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 2

        let semaphore = DispatchSemaphore(value: 0)
        var ok = false
        URLSession.shared.dataTask(with: request) { _, response, _ in
            if let http = response as? HTTPURLResponse {
                ok = http.statusCode == 200
            }
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 3)
        return ok
    }
}