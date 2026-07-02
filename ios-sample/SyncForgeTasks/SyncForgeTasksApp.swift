import SwiftUI

@main
struct SyncForgeTasksApp: App {
    @StateObject private var viewModel = SampleViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(viewModel)
        }
    }
}