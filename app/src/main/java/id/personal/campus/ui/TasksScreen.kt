package id.personal.campus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import id.personal.campus.data.CampusSnapshot
import id.personal.campus.data.CourseEntity
import id.personal.campus.data.TaskEntity
import java.time.LocalDate

fun taskDeadline(task: TaskEntity, today: LocalDate): String {
    val due = task.dueEpochDay ?: return "Tanpa tenggat"
    return when {
        task.completed -> "Tenggat ${dateLabel(due)}"
        due < today.toEpochDay() -> "Lewat tenggat · ${dateLabel(due)}"
        due == today.toEpochDay() -> "Tenggat hari ini"
        due == today.plusDays(1).toEpochDay() -> "Tenggat besok"
        else -> "Tenggat ${dateLabel(due)}"
    }
}

@Composable
fun TaskRow(task: TaskEntity, courses: List<CourseEntity>, today: LocalDate, busy: Boolean, onEdit: () -> Unit, onToggle: (TaskEntity) -> Unit) {
    val course = courses.find { it.id == task.courseId }
    val overdue = !task.completed && task.dueEpochDay?.let { it < today.toEpochDay() } == true
    val dueTone = when {
        task.dueEpochDay == null -> null
        task.completed -> BadgeTone.Neutral
        overdue -> BadgeTone.Danger
        task.dueEpochDay <= today.toEpochDay() + 1 -> BadgeTone.Butter
        task.dueEpochDay <= today.toEpochDay() + 6 -> BadgeTone.Brand
        else -> BadgeTone.Neutral
    }
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(1.dp, if (overdue) Danger500.copy(alpha = 0.3f) else Line),
        shape = ListShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            CheckCircle(
                checked = task.completed, enabled = !busy,
                onCheckedChange = { onToggle(task.copy(completed = it)) },
                modifier = Modifier.padding(top = 2.dp).semantics { contentDescription = "${if (task.completed) "Buka kembali" else "Selesaikan"} tugas ${task.title}" },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    task.title, style = MaterialTheme.typography.titleMedium,
                    color = if (task.completed) Muted else Ink,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dueTone != null) Badge(taskDeadline(task, today), dueTone)
                    if (course != null) Badge(course.name, BadgeTone.Neutral, Modifier.weight(1f, fill = false))
                    if (dueTone == null && course == null) Text("Tanpa tenggat", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodySmall, color = Ink2, maxLines = 2)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    KarsaChip(label = label, selected = selected, onClick = onClick, leading = true)
}

@Composable
fun TasksScreen(data: CampusSnapshot, today: LocalDate, busy: Boolean, onAdd: () -> Unit, onEdit: (Long) -> Unit, onToggle: (TaskEntity) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("active") }
    val tasks = data.tasks.filter {
        when (filter) { "active" -> !it.completed; "done" -> it.completed; else -> true }
    }.sortedWith(compareBy<TaskEntity> { it.completed }.thenBy { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.id })
    val enter by rememberEnterProgress()
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = enter
            translationY = riseProgress(enter, 10f) * density
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading("Tugas", "${data.tasks.count { !it.completed }} belum selesai · ${data.tasks.count { it.completed }} selesai", eyebrow = "Daftar pribadi") }
        item {
            KarsaButton(
                "Tambah tugas",
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                size = KarsaButtonSize.Lg,
                icon = Icons.Outlined.Add,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Belum selesai", filter == "active") { filter = "active" }
                FilterChip("Selesai", filter == "done") { filter = "done" }
                FilterChip("Semua tugas", filter == "all") { filter = "all" }
            }
        }
        if (tasks.isEmpty()) item {
            EmptyPanel(
                if (filter == "done") "Belum ada tugas selesai" else "Ruang untuk bernapas",
                if (data.tasks.isEmpty()) "Catat tugas pertama Anda, dengan atau tanpa tenggat." else "Tidak ada tugas pada pilihan ini. Anda bisa mengganti tampilan.",
            )
        }
        items(tasks, key = { it.id }) { task ->
            // Baris meluncur halus saat filter berubah (selesai ⇄ belum selesai).
            Box(Modifier.animateItem()) {
                TaskRow(task, data.courses, today, busy, { onEdit(task.id) }, onToggle)
            }
        }
        item { Text("Daftar pribadi, tersimpan offline. Notifikasi pengingat hanya untuk jadwal kuliah.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}
