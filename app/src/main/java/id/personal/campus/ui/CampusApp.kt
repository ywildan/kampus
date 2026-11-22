package id.personal.campus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.plus
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.TaskEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

// Tab Asisten
import id.personal.campus.ui.AgentChatScreen
import id.personal.campus.ui.AgentChatViewModel

private val DockItems = listOf(
    DockItem("Hari Ini", Icons.Outlined.Today),
    DockItem("Jadwal", Icons.Outlined.CalendarMonth),
    DockItem("Asisten", Icons.Outlined.AutoAwesome),
    DockItem("Tugas", Icons.Outlined.Checklist),
    DockItem("Pengaturan", Icons.Outlined.Settings),
)

/**
 * Akar aplikasi: konten utama ([CampusAppContent]) ditutup splash oranye bermotif Truntum
 * selama minimal 1,3 detik atau sampai data pertama selesai dimuat, lalu memudar.
 */
@Composable
fun CampusApp(model: CampusViewModel, chatModel: AgentChatViewModel) {
    val loaded by model.state.collectAsStateWithLifecycle()
    var minElapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!minElapsed) {
            delay(1_300)
            minElapsed = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        CampusAppContent(model, chatModel)
        // Splash memudar sambil mengecil sedikit supaya konten di belakangnya terasa "terangkat",
        // bukan tertutup lapisan yang hilang mendadak.
        AnimatedVisibility(
            visible = !minElapsed || (loaded.loading && loaded.data == null),
            exit = fadeOut(tween(KarsaMotion.Long, easing = KarsaMotion.Exit)) +
                scaleOut(tween(KarsaMotion.Long, easing = KarsaMotion.Exit), targetScale = 1.06f),
        ) { SplashScreen() }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CampusAppContent(model: CampusViewModel, chatModel: AgentChatViewModel) {
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
    // Kembali dari Setelan: muat ulang model/kesiapan Asisten supaya nama di sheet tidak basi.
    LaunchedEffect(tab) {
        if (tab == 2) chatModel.refreshSetup()
    }

    LaunchedEffect(navigation, data != null) {
        if (data == null || navigation == null) return@LaunchedEffect
        courseEditorId = null
        taskEditorId = null
        noteEditorId = null
        detailId = null
        if (navigation == "settings") {
            tab = 4
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
            // Urutan tab: Asisten di tengah (indeks 2 dari 5), tombolnya menonjol dari Dock.
            Box(
                Modifier.fillMaxWidth().background(Paper)
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            ) {
                KarsaDock(DockItems, selected = tab, enabled = data != null, onSelect = { tab = it })
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
                        LoadingRing(size = 28.dp)
                        Text("Membuka ruang kuliah Anda…", color = Ink2)
                    } else {
                        Text(state.loadError ?: "Data belum tersedia.", textAlign = TextAlign.Center, color = Ink2)
                        KarsaButton("Coba lagi", onClick = model::load, icon = Icons.Outlined.Refresh)
                    }
                }
            } else {
                if (state.loadError != null) {
                    KarsaButton(
                        "Pembaruan data terhenti · Coba lagi",
                        onClick = model::load,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        variant = KarsaButtonVariant.Soft,
                    )
                }
                // Perpindahan tab: isi layar bergeser sedikit ke arah tab tujuan sambil menyilang
                // memudar, dan arah geser mengikuti urutan tab (kiri → kanan) agar ruang terasa
                // punya satu sumbu, bukan lima layar yang saling menimpa.
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val enter = slideInHorizontally(
                            animationSpec = tween(KarsaMotion.Medium, easing = KarsaMotion.Emphasized),
                            initialOffsetX = { width -> (width / 14) * if (forward) 1 else -1 },
                        ) + fadeIn(tween(KarsaMotion.Medium, easing = KarsaMotion.Standard))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(KarsaMotion.Quick + 60, easing = KarsaMotion.Exit),
                            targetOffsetX = { width -> -(width / 20) * if (forward) 1 else -1 },
                        ) + fadeOut(tween(KarsaMotion.Quick, easing = KarsaMotion.Exit))
                        enter togetherWith exit
                    },
                    label = "tabContent",
                    contentKey = { it },
                ) { current ->
                    tabStates.SaveableStateProvider(current) {
                    when (current) {
                        0 -> HomeScreen(
                            data, checkNotNull(state.schedule), state.saving,
                            onSettings = { tab = 4 }, onCourse = { detailId = it },
                            onTasks = { tab = 3 }, onTask = { taskEditorId = it },
                            onTaskCompleted = { model.saveTask(it) },
                            onAddNote = { noteEditorId = -1 }, onNote = { noteEditorId = it },
                            onAssistant = { tab = 2 },
                        )
                        1 -> ScheduleScreen(data.courses, data.settings.zoneId, state.saving,
                            onAdd = { courseEditorId = -1 }, onOpen = { detailId = it },
                            onToggle = { model.saveCourse(it) })
                        2 -> AgentChatScreen(
                            state = chatModel.state.collectAsStateWithLifecycle().value,
                            onInput = chatModel::onInput,
                            onModelMode = chatModel::chooseModelMode,
                            onSend = chatModel::send,
                            onStop = chatModel::stop,
                            onApprove = chatModel::approve,
                            onDeny = chatModel::deny,
                            onUndo = chatModel::undoLastWrite,
                            onConsumeNotice = chatModel::consumeNotice,
                            onOpenSettings = { tab = 4 },
                            onRefreshSetup = chatModel::refreshSetup,
                            onRetry = chatModel::retry,
                        )
                        3 -> TasksScreen(data, checkNotNull(state.schedule).now.toLocalDate(), state.saving,
                            onAdd = { taskEditorId = -1 }, onEdit = { taskEditorId = it },
                            onToggle = { model.saveTask(it) })
                        4 -> SettingsScreen(data.settings, state.access, state.saving,
                            today = checkNotNull(state.schedule).now.toLocalDate(),
                            onSave = { settings, done -> model.saveSettings(settings, done) },
                            onPermissionResult = { model.onForeground() }, onNotice = model::notify,
                            agent = agentState,
                            onSaveAgent = { config, newKey, clear -> model.saveAgent(config, newKey, clear) },
                            onCheckAgent = { config, newKey -> model.checkAgent(config, newKey) })
                    }
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
