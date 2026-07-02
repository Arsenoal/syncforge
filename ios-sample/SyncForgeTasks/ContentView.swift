import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            TasksView()
                .tabItem {
                    Label("Tasks", systemImage: "checklist")
                }

            NotesView()
                .tabItem {
                    Label("Notes", systemImage: "note.text")
                }

            TagsView()
                .tabItem {
                    Label("Tags", systemImage: "tag")
                }
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