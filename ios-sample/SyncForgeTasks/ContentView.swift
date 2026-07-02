import SwiftUI

struct ContentView: View {
    var body: some View {
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
        .safeAreaInset(edge: .top) {
            SampleStatusBanner()
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 4)
                .background(Color(UIColor.systemBackground))
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