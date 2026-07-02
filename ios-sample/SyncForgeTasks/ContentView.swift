import SwiftUI

struct ContentView: View {
    var body: some View {
        TasksView()
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