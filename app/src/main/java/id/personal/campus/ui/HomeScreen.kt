package id.personal.campus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.TaskEntity
import id.personal.campus.schedule.Occurrence
import id.personal.campus.schedule.ScheduleMode
import id.personal.campus.schedule.ScheduleSnapshot
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    data: CampusSnapshot,
    schedule: ScheduleSnapshot,
    busy: Boolean,
    onSettings: () -> Unit,
    onCourse: (Long) -> Unit,
    onTasks: () -> Unit,
    onTask: (Long) -> Unit,
    onTaskCompleted: (TaskEntity) -> Unit,
    onAddNote: () -> Unit,
    onNote: (Long) -> Unit,
) {
    val nearTasks = data.tasks.filter { !it.completed }.sortedWith(
        compareBy<TaskEntity> { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.id },
    ).take(3)
    val preview = !data.settings.semesterConfirmed
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            PageHeading("Hari ini", schedule.now.format(FullDateFormat))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule.now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), style = TimeStyle.copy(fontSize = 36.sp))
                Text("${zoneLabel(data.settings.zoneId)} · ${data.settings.semesterName}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
        if (preview) item {
            PaperPanel {
                Text("Jadwal masih pratinjau", style = MaterialTheme.typography.titleLarge)
                Text("Tanggal semester belum dikonfirmasi. Anda boleh melihat dan mengedit jadwal sekarang; pengingat belum berjalan.")
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Atur tanggal semester") }
            }
        }
        if (schedule.active.size > 1) item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    if (preview) "Bentrok pada pratinjau: ${schedule.active.size} kuliah memiliki jam yang bertumpuk."
                    else "Jadwal bentrok: ${schedule.active.size} kuliah berlangsung bersamaan. Periksa seluruh ruang di bawah.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        items(schedule.active, key = { "active-${it.key}" }) { occurrence ->
            OccurrencePanel(occurrence, if (preview) "PRATINJAU · JAM SAAT INI" else "BERLANGSUNG", data.settings.zoneId, true) { onCourse(occurrence.course.id) }
        }
        schedule.upcoming.firstOrNull()?.let { next ->
            item(key = "next-${next.key}") {
                OccurrencePanel(next, if (preview) "PRATINJAU · BERIKUTNYA" else "BERIKUTNYA", data.settings.zoneId, schedule.active.isEmpty()) { onCourse(next.course.id) }
            }
        }
        if (schedule.primary == null) item {
            PaperPanel {
                val heading = when (schedule.mode) {
                    ScheduleMode.EMPTY -> "Tidak ada kuliah aktif"
                    ScheduleMode.FINISHED -> "Semester telah selesai"
                    else -> "Pertemuan semester sudah berakhir"
                }
                Text(heading, style = MaterialTheme.typography.headlineSmall)
                Text(when (schedule.mode) {
                    ScheduleMode.EMPTY -> "Tambahkan mata kuliah atau aktifkan jadwal dari tab Jadwal."
                    ScheduleMode.FINISHED -> "Atur rentang semester berikutnya untuk melanjutkan jadwal dan pengingat."
                    else -> "Tidak ada pertemuan berikutnya dalam rentang semester yang dipilih."
                })
                if (schedule.mode != ScheduleMode.EMPTY) Button(onClick = onSettings) { Text("Tinjau semester") }
            }
        }
        item { SectionHeading("Tugas terdekat", "Lihat semua", onTasks) }
        if (nearTasks.isEmpty()) item {
            PaperPanel { Text("Tidak ada tugas tertunda."); Text("Nikmati jeda, atau catat tugas baru di tab Tugas.", color = MutedInk) }
        }
        items(nearTasks, key = { "task-${it.id}" }) { task ->
            TaskRow(task, data.courses, schedule.now.toLocalDate(), busy, { onTask(task.id) }, onTaskCompleted)
        }
        item { SectionHeading("Informasi pribadi", "Tambah", onAddNote) }
        item { Text("Catatan yang Anda isi sendiri, bukan informasi resmi dari kampus.", style = MaterialTheme.typography.bodySmall, color = MutedInk) }
        if (data.notes.isEmpty()) item {
            PaperPanel {
                Text("Simpan hal yang perlu diingat", style = MaterialTheme.typography.titleLarge)
                Text("Misalnya lokasi dosen, daftar bacaan, atau informasi administrasi.")
            }
        }
        items(data.notes.sortedByDescending { it.id }, key = { "note-${it.id}" }) { note ->
            Surface(
                onClick = { onNote(note.id) }, modifier = Modifier.fillMaxWidth(), color = Paper,
                border = BorderStroke(1.dp, Rule), shape = MaterialTheme.shapes.medium,
                shadowElevation = 0.dp, tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(note.title, style = MaterialTheme.typography.titleLarge)
                    if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyLarge)
                    Text("Edit atau hapus catatan", color = CampusGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item { Text("Kampus · Ruang pribadi, sepenuhnya offline.", modifier = Modifier.padding(top = 8.dp), color = MutedInk, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun OccurrencePanel(occurrence: Occurrence, label: String, zoneId: String, prominent: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = if (prominent) MaterialTheme.colorScheme.primaryContainer else Paper,
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, if (prominent) CampusGreen else Rule),
        shadowElevation = 0.dp, tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = CampusGreen)
            Text(occurrence.start.format(FullDateFormat), style = MaterialTheme.typography.bodyMedium)
            Text("${minuteLabel(occurrence.course.startMinute)}–${minuteLabel(occurrence.course.endMinute)}", style = TimeStyle.copy(fontSize = 30.sp))
            Text("RUANG · ${zoneLabel(zoneId)}", style = MaterialTheme.typography.labelMedium, color = MutedInk)
            Text(occurrence.course.room, style = TimeStyle.copy(fontSize = 34.sp), color = CampusGreen)
            Text(occurrence.course.name, style = MaterialTheme.typography.titleLarge)
            if (occurrence.course.section.isNotBlank()) Text("Kelas ${occurrence.course.section}", style = MaterialTheme.typography.bodyMedium)
            if (occurrence.course.roomNote.isNotBlank()) Text(occurrence.course.roomNote)
            Text("Buka detail mata kuliah", color = CampusGreen, style = MaterialTheme.typography.labelLarge)
        }
    }
}
