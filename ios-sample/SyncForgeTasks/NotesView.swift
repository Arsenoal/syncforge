import SwiftUI

struct NotesView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(spacing: 12) {
            addNoteSection
            noteList
        }
        .padding(.horizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var addNoteSection: some View {
        VStack(spacing: 8) {
            TextField("Title", text: $viewModel.newNoteTitle)
                .textFieldStyle(RoundedBorderTextFieldStyle())

            TextField("Body (optional)", text: $viewModel.newNoteBody)
                .textFieldStyle(RoundedBorderTextFieldStyle())

            Button("Add note", action: viewModel.addNote)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var noteList: some View {
        Group {
            if viewModel.notes.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "note.text")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("No notes yet")
                        .font(.headline)
                    Text("Add a note, then tap Sync to push to the mock server.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.notes) { note in
                        NoteRowView(note: note) {
                            viewModel.deleteNote(note)
                        }
                    }
                }
                .listStyle(PlainListStyle())
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#if DEBUG
struct NotesView_Previews: PreviewProvider {
    static var previews: some View {
        NotesView()
            .environmentObject(SampleViewModel())
    }
}
#endif