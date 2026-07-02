package dev.syncforge.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.syncforge.sample.navigation.SampleApp
import dev.syncforge.sample.notes.NotesViewModel
import dev.syncforge.sample.tags.TagsViewModel
import dev.syncforge.sample.tasks.TasksViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SampleApplication
        val tasksViewModel = TasksViewModel(
            repository = app.taskRepository,
            syncManager = app.syncManager,
        )
        val notesViewModel = NotesViewModel(repository = app.noteRepository)
        val tagsViewModel = TagsViewModel(repository = app.tagRepository)

        setContent {
            SampleApp(
                tasksViewModel = tasksViewModel,
                notesViewModel = notesViewModel,
                tagsViewModel = tagsViewModel,
                syncManager = app.syncManager,
                onSync = {
                    lifecycleScope.launch { app.syncManager.sync() }
                },
            )
        }
    }
}