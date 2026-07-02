import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.kotlinBridgeReady {
                Text("ready")
                    .font(.system(size: 1))
                    .foregroundColor(.clear)
                    .frame(width: 1, height: 1)
                    .accessibilityIdentifier("e2e_kotlin_ready")
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Kotlin bridge ready")
            }

            SampleStatusBanner()
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 4)
                .background(Color(UIColor.systemBackground))

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
        }
        .accessibilityIdentifier("syncforge_tasks_root")
        .accessibilityElement(children: .contain)
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