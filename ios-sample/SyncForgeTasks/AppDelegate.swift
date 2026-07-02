import SyncForge
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosBackgroundSyncKt.registerIosBackgroundSyncTasks(taskIdentifier: SampleConfig.backgroundSyncTaskId)
        return true
    }
}