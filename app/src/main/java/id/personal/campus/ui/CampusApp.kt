package id.personal.campus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.TaskEntity
import kotlinx.coroutines.flow.collect

// Tab Asisten
import id.personal.campus.ui.AgentChatScreen
import id.personal.campus.ui.AgentChatViewModel

@Composable
fun CampusApp(model: CampusViewModel, chatModel: AgentChatViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val agentState by model.agent.collectAsStateWithLifecycle()
    val navigation by model.navigation.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val tabStates = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var courseEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var taskEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var noteEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    val data = state.data
    val modalOpen = data != null && (detailId != null || courseEditorId != null || taskEditorId != null || noteEditorId != null)

    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.notices.collect { snackbar.showSnackbar(it, actionLabel = "Tutup", duration = SnackbarDuration.Long) }
        }
    }
    LaunchedEffect(navigation, data != null) {
        if (data == null || navigation == null) return@LaunchedEffect
        courseEditorId = null
        taskEditorId = null
        noteEditorId = null
        detailId = null
        if (navigation == "settings") {
            tab = 3
        } else {
            val id = navigation?.removePrefix("course:")?.toLongOrNull()
            tab = 1
            if (data.courses.any { it.id == id }) detailId = id
            else model.notify("Mata kuliah ini sudah dihapus atau tidak tersedia.")
        }
        model.consumeNavigation()
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { if (!modalOpen) SnackbarHost(snackbar) },
        bottomBar = {
            val labels = listOf("Hari Ini", "Jadwal", "Tugas", "Pengaturan", "Asisten")
            val icons = listOf(Icons.Outlined.Today, Icons.Outlined.CalendarMonth, Icons.Outlined.Checklist, Icons.Outlined.Settings, Icons.Outlined.SmartToy)
            Surface(color = Paper, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    HorizontalDivider(color = Rule)
                    Row(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { index, label ->
                            Column(
                                modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                    .selectable(selected = tab == index, enabled = data != null, role = Role.Tab, onClick = { tab = index })
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val color = if (tab == index) CampusGreen else MutedInk
                                Icon(icons[index], contentDescription = null, tint = color)
                                Text(label, color = color, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (state.loading || data == null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator()
                        Text("Membuka ruang kuliah Anda…")
                    } else {
                        Text(state.loadError ?: "Data belum tersedia.", textAlign = TextAlign.Center)
                        Button(onClick = model::load) { Text("Coba lagi") }
                    }
                }
            } else {
                if (state.loadError != null) {
                    Button(onClick = model::load, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text("Pembaruan data terhenti · Coba lagi")
                    }
                }
                tabStates.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> HomeScreen(
                            data, checkNotNull(state.schedule), state.saving,
                            onSettings = { tab = 3 }, onCourse = { detailId = it },
                            onTasks = { tab = 2 }, onTask = { taskEditorId = it },
                            onTaskCompleted = { model.saveTask(it) },
                            onAddNote = { noteEditorId = -1 }, onNote = { noteEditorId = it },
                        )
                        1 -> ScheduleScreen(data.courses, data.settings.zoneId, state.saving,
                            onAdd = { courseEditorId = -1 }, onOpen = { detailId = it },
                            onToggle = { model.saveCourse(it) })
                        2 -> TasksScreen(data, checkNotNull(state.schedule).now.toLocalDate(), state.saving,
                            onAdd = { taskEditorId = -1 }, onEdit = { taskEditorId = it },
                            onToggle = { model.saveTask(it) })
                        3 -> SettingsScreen(data.settings, state.access, state.saving,
                            today = checkNotNull(state.schedule).now.toLocalDate(),
                            onSave = { settings, done -> model.saveSettings(settings, done) },
                            onPermissionResult = { model.onForeground() }, onNotice = model::notify,
                            agent = agentState,
                            onSaveAgent = { config, newKey, clear -> model.saveAgent(config, newKey, clear) },
                            onCheckAgent = { fetchModels -> model.checkAgent(fetchModels) })
                        4 -> AgentChatScreen(
                            state = chatModel.state.collectAsStateWithLifecycle().value,
                            onInput = chatModel::onInput,
                            onSkill = chatModel::chooseSkill,
                            onSmartToggle = chatModel::toggleSmartModel,
                            onSend = chatModel::send,
                            onStop = chatModel::stop,
                            onApprove = chatModel::approve,
                            onDeny = chatModel::deny,
                            onUndo = chatModel::undoLastWrite,
                            onConsumeNotice = chatModel::consumeNotice,
                            onOpenSettings = { tab = 3 },
                            onRefreshSetup = chatModel::refreshSetup,
                        )
                    }
                }
            }
        }
    }

    if (data != null) {
        detailId?.let { id ->
            data.courses.find { it.id == id }?.let { course ->
                CourseDetails(course, data.settings.zoneId, state.saving, snackbar,
                    onDismiss = { detailId = null },
                    onEdit = { detailId = null; courseEditorId = id },
                    onToggle = { model.saveCourse(it) },
                    onDelete = { model.deleteCourse(id) { detailId = null } })
            }
        }
        courseEditorId?.let { id ->
            val course = if (id == -1L) CourseEntity(name = "", weekday = 1, startMinute = 480, endMinute = 580, room = "")
                else data.courses.find { it.id == id }
            if (course != null) CourseEditor(course, data.settings.zoneId, state.saving, snackbar,
                onDismiss = { courseEditorId = null },
                onSave = { model.saveCourse(it) { courseEditorId = null } },
                onDelete = { model.deleteCourse(course.id) { courseEditorId = null } })
        }
        taskEditorId?.let { id ->
            val task = if (id == -1L) TaskEntity(title = "") else data.tasks.find { it.id == id }
            if (task != null) TaskEditor(task, data.courses, checkNotNull(state.schedule).now.toLocalDate(), state.saving, snackbar,
                onDismiss = { taskEditorId = null },
                onSave = { model.saveTask(it) { taskEditorId = null } },
                onDelete = { model.deleteTask(task.id) { taskEditorId = null } })
        }
        noteEditorId?.let { id ->
            val note = if (id == -1L) NoteEntity(title = "") else data.notes.find { it.id == id }
            if (note != null) NoteEditor(note, state.saving, snackbar,
                onDismiss = { noteEditorId = null },
                onSave = { model.saveNote(it) { noteEditorId = null } },
                onDelete = { model.deleteNote(note.id) { noteEditorId = null } })
        }
    }
}
