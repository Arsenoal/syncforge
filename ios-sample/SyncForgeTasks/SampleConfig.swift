import Foundation

enum SampleConfig {
    /// Matches [IOS_SAMPLE_DEFAULT_BASE_URL] in `:sample-ios-shared`.
    static let defaultBaseUrl = "http://127.0.0.1:8080"

    /// Matches [IOS_SAMPLE_BACKGROUND_SYNC_TASK_ID] in `:sample-ios-shared` / Info.plist.
    static let backgroundSyncTaskId = "dev.syncforge.sample.ios.refresh"

    /// XCUITest / CI — env or launch argument from [SampleUITestBase].
    static var isE2eTesting: Bool {
        ProcessInfo.processInfo.environment["E2E_TESTING"] == "1"
            || ProcessInfo.processInfo.arguments.contains("-E2E_TESTING")
    }
}