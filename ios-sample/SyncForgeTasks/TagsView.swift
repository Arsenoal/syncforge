import SwiftUI

struct TagsView: View {
    @EnvironmentObject private var viewModel: SampleViewModel

    var body: some View {
        VStack(spacing: 12) {
            addTagSection
            tagList
        }
        .padding(.horizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var addTagSection: some View {
        HStack(spacing: 8) {
            AccessibleTextField(
                placeholder: "New tag",
                text: $viewModel.newTagLabel,
                accessibilityIdentifier: "new_tag_input",
                onCommit: viewModel.addTag
            )

            Button("Add", action: viewModel.addTag)
                .accessibilityIdentifier("add_tag_button")
        }
    }

    private var tagList: some View {
        Group {
            if viewModel.tags.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "tag")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("No tags yet")
                        .font(.headline)
                    Text("Add a tag, then tap Sync to push to the mock server.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(viewModel.tags) { tag in
                        TagRowView(tag: tag) {
                            viewModel.deleteTag(tag)
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
struct TagsView_Previews: PreviewProvider {
    static var previews: some View {
        TagsView()
            .environmentObject(SampleViewModel())
    }
}
#endif