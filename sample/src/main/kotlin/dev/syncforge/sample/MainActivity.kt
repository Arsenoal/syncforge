package dev.syncforge.sample

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.syncforge.sample.demo.DemoRecordingRunner
import dev.syncforge.sample.demo.attachDemoPresentation
import dev.syncforge.sample.navigation.SampleApp
import kotlinx.coroutines.delay
import dev.syncforge.sample.notes.NotesViewModel
import dev.syncforge.sample.tags.TagsViewModel
import dev.syncforge.sample.tasks.TasksViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    private val autoDemo: Boolean
        get() = intent.getBooleanExtra(DemoRecordingRunner.EXTRA_AUTO_DEMO, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (autoDemo) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val app = application as SampleApplication
        val tasksViewModel = TasksViewModel(
            repository = app.taskRepository,
            syncManager = app.syncManager,
        )
        val notesViewModel = NotesViewModel(
            repository = app.noteRepository,
            tagRepository = app.tagRepository,
        )
        val tagsViewModel = TagsViewModel(repository = app.tagRepository)

        lifecycleScope.attachDemoPresentation(
            syncManager = app.syncManager,
            observeTaskCount = { app.countTasks() },
        )

        setContent {
            SampleApp(
                tasksViewModel = tasksViewModel,
                notesViewModel = notesViewModel,
                tagsViewModel = tagsViewModel,
                syncManager = app.syncManager,
                onSync = {
                    lifecycleScope.launch { app.syncManager.sync() }
                },
                onClearLocalData = { app.resetForDemoPresentation() },
            )
        }

        if (autoDemo) {
            lifecycleScope.launch {
                delay(1_000.milliseconds)
                DemoRecordingRunner.run(app)
            }
        }
    }
}