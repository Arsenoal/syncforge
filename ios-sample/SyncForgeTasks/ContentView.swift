import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.kotlinBridgeReady {
                Color.clear
                    .frame(width: 0, height: 0)
                    .accessibilityIdentifier("e2e_kotlin_ready")
            }

            SampleStatusBanner()
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 4)
                .background(Color(UIColor.systemBackground))

            TabView {
                TasksView()
                    .accessibilityIdentifier("nav_tasks_screen")
                    .tabItem {
                        Label("Tasks", systemImage: "checklist")
                    }
                    .accessibilityIdentifier("nav_tasks")

                NotesView()
                    .accessibilityIdentifier("nav_notes_screen")
                    .tabItem {
                        Label("Notes", systemImage: "note.text")
                    }
                    .accessibilityIdentifier("nav_notes")

                TagsView()
                    .accessibilityIdentifier("nav_tags_screen")
                    .tabItem {
                        Label("Tags", systemImage: "tag")
                    }
                    .accessibilityIdentifier("nav_tags")
            }
        }
        .accessibilityIdentifier("syncforge_tasks_root")
        .onAppear {
            viewModel.beginKotlinPreloadIfNeeded()
        }
    }
}

#if DEBUG
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(SampleViewModel())
    }
}
#endif