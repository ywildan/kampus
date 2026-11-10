package id.personal.campus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.NoteEntity
import id.personal.campus.data.TaskEntity
import java.time.LocalDate

@Composable
fun EditorFrame(
    title: String,
    busy: Boolean,
    dirty: Boolean,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    deleteExplanation: String = "Data ini akan dihapus permanen dari perangkat.",
    content: @Composable ColumnScope.() -> Unit,
) {
    var discard by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val requestClose = { if (!busy) { if (dirty) discard = true else onDismiss() } }
    Dialog(onDismissRequest = requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { requestClose() }
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = !busy, onClick = requestClose) { Icon(Icons.Outlined.Close, contentDescription = "Tutup formulir") }
                    Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider(color = Rule)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
                )
                SnackbarHost(snackbar, Modifier.fillMaxWidth())
                if (onSave != null || onDelete != null) {
                    HorizontalDivider(color = Rule)
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onDelete != null) TextButton(enabled = !busy, onClick = { delete = true }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                        if (onSave != null) Button(modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy, onClick = onSave) {
                            Text(if (busy) "Menyimpan…" else "Simpan")
                        }
                    }
                }
            }
        }
        if (discard) AlertDialog(
            onDismissRequest = { discard = false }, title = { Text("Abaikan perubahan?") },
            text = { Text("Isian yang belum disimpan akan hilang.") },
            confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { Text("Abaikan") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Lanjut mengisi") } },
            containerColor = Paper, tonalElevation = 0.dp,
        )
        if (delete && onDelete != null) DeleteConfirmation(
            title = "data ini", explanation = deleteExplanation, busy = busy,
            onDismiss = { delete = false }, onConfirm = onDelete,
        )
    }
}

@Composable
fun <T> ChoiceField(label: String, selected: T, options: List<Pair<T, String>>, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, onClick = { expanded = true }) {
            Text("$label: ${options.find { it.first == selected }?.second ?: "Belum dipilih"}", Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp), containerColor = Paper,
            tonalElevation = 0.dp, shadowElevation = 0.dp,
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun ToggleField(title: String, description: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun CourseEditor(course: CourseEntity, zoneId: String, busy: Boolean, snackbar: SnackbarHostState, onDismiss: () -> Unit, onSave: (CourseEntity) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable(course.id) { mutableStateOf(course.name) }
    var weekday by rememberSaveable(course.id) { mutableStateOf(course.weekday) }
    var start by rememberSaveable(course.id) { mutableStateOf(course.startMinute) }
    var end by rememberSaveable(course.id) { mutableStateOf(course.endMinute) }
    var room by rememberSaveable(course.id) { mutableStateOf(course.room) }
    var section by rememberSaveable(course.id) { mutableStateOf(course.section) }
    var roomNote by rememberSaveable(course.id) { mutableStateOf(course.roomNote) }
    var enabled by rememberSaveable(course.id) { mutableStateOf(course.enabled) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val draft = course.copy(name = name, weekday = weekday, startMinute = start, endMinute = end, room = room, section = section, roomNote = roomNote, enabled = enabled)
    val valid = name.isNotBlank() && room.isNotBlank() && end > start
    EditorFrame(
        title = if (course.id == 0L) "Tambah mata kuliah" else "Edit mata kuliah",
        busy = busy, dirty = draft != course, snackbar = snackbar, onDismiss = onDismiss,
        onSave = { attempted = true; if (valid) onSave(draft) },
        onDelete = if (course.id != 0L) onDelete else null,
        deleteExplanation = "Mata kuliah ini dihapus. Tugas terkait tetap disimpan tanpa tautan mata kuliah.",
    ) {
        Text("Jadwal mingguan · ${zoneLabel(zoneId)}", color = MutedInk)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nama mata kuliah") }, singleLine = true, isError = attempted && name.isBlank())
        if (attempted && name.isBlank()) ValidationText("Nama mata kuliah wajib diisi.")
        ChoiceField("Hari", weekday, DayNames.mapIndexed { index, day -> index + 1 to day }, !busy) { weekday = it }
        TimeField("Jam mulai", start, !busy) { start = it }
        TimeField("Jam selesai", end, !busy) { end = it }
        if (end <= start) ValidationText("Jam selesai harus setelah jam mulai pada hari yang sama.")
        OutlinedTextField(room, { room = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Kode ruang") }, singleLine = true, isError = attempted && room.isBlank())
        if (attempted && room.isBlank()) ValidationText("Kode ruang wajib diisi.")
        OutlinedTextField(section, { section = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Kelas / seksi (opsional)") }, singleLine = true)
        OutlinedTextField(roomNote, { roomNote = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Keterangan ruang (opsional)") }, minLines = 2)
        ToggleField("Mata kuliah aktif", "Nonaktif tetap tersimpan di jadwal, tetapi tidak masuk pertemuan berikutnya atau pengingat.", enabled, !busy) { enabled = it }
        Text("Perulangan mengikuti rentang semester di Pengaturan. Jadwal lintas tengah malam tidak didukung.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
    }
}

@Composable
fun TaskEditor(task: TaskEntity, courses: List<CourseEntity>, today: LocalDate, busy: Boolean, snackbar: SnackbarHostState, onDismiss: () -> Unit, onSave: (TaskEntity) -> Unit, onDelete: () -> Unit) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var courseId by rememberSaveable(task.id) { mutableStateOf(task.courseId) }
    var due by rememberSaveable(task.id) { mutableStateOf(task.dueEpochDay) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes) }
    var completed by rememberSaveable(task.id) { mutableStateOf(task.completed) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val safeCourseId = courseId?.takeIf { selected -> courses.any { it.id == selected } }
    val draft = task.copy(title = title, courseId = safeCourseId, dueEpochDay = due, notes = notes, completed = completed)
    EditorFrame(
        title = if (task.id == 0L) "Tambah tugas" else "Edit tugas", busy = busy, dirty = draft != task,
        snackbar = snackbar, onDismiss = onDismiss,
        onSave = { attempted = true; if (title.isNotBlank()) onSave(draft) },
        onDelete = if (task.id != 0L) onDelete else null,
    ) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Judul tugas") }, isError = attempted && title.isBlank())
        if (attempted && title.isBlank()) ValidationText("Judul tugas wajib diisi.")
        val choices: List<Pair<Long?, String>> = listOf(null to "Tanpa mata kuliah") + courses.sortedBy { it.name }.map {
            it.id to "${it.name} · ${DayNames[it.weekday - 1]}${if (it.enabled) "" else " · Nonaktif"}"
        }
        ChoiceField("Mata kuliah", safeCourseId, choices, !busy) { courseId = it }
        DateField("Tenggat", due, today, { due = it }, enabled = !busy)
        Text("Tenggat berupa tanggal di zona jadwal. Tugas tidak mengirim pengingat notifikasi.", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Catatan tugas (opsional)") }, minLines = 4)
        ToggleField("Tugas selesai", "Matikan untuk mengembalikan ke daftar tugas aktif.", completed, !busy) { completed = it }
    }
}

@Composable
fun NoteEditor(note: NoteEntity, busy: Boolean, snackbar: SnackbarHostState, onDismiss: () -> Unit, onSave: (NoteEntity) -> Unit, onDelete: () -> Unit) {
    var title by rememberSaveable(note.id) { mutableStateOf(note.title) }
    var body by rememberSaveable(note.id) { mutableStateOf(note.body) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val draft = note.copy(title = title, body = body)
    EditorFrame(
        title = if (note.id == 0L) "Tambah informasi pribadi" else "Edit informasi pribadi",
        busy = busy, dirty = draft != note, snackbar = snackbar, onDismiss = onDismiss,
        onSave = { attempted = true; if (title.isNotBlank()) onSave(draft) },
        onDelete = if (note.id != 0L) onDelete else null,
    ) {
        Text("Catatan manual Anda, bukan pengumuman resmi kampus. Tersimpan hanya di perangkat.", color = MutedInk)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Judul informasi") }, isError = attempted && title.isBlank())
        if (attempted && title.isBlank()) ValidationText("Judul informasi wajib diisi.")
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Isi catatan (opsional)") }, minLines = 6)
    }
}
