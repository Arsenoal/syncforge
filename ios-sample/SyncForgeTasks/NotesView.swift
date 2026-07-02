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
            AccessibleTextField(
                placeholder: "Title",
                text: $viewModel.newNoteTitle,
                accessibilityIdentifier: "new_note_title_input"
            )

            AccessibleTextField(
                placeholder: "Body (optional)",
                text: $viewModel.newNoteBody,
                accessibilityIdentifier: "new_note_body_input"
            )

            Button("Add note", action: viewModel.addNote)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("add_note_button")
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