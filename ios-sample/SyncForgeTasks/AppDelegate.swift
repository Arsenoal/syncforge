import SyncForge
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // XCUITest: skip BGTask registration so launch reaches idle and UI is queryable.
        if ProcessInfo.processInfo.environment["E2E_TESTING"] != "1" {
            IosBackgroundSyncKt.registerIosBackgroundSyncTasks(taskIdentifier: SampleConfig.backgroundSyncTaskId)
        }
        return true
    }
}