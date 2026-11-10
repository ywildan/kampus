package id.personal.campus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    Surface(
        onClick = onEdit, modifier = Modifier.fillMaxWidth(), color = Paper,
        border = BorderStroke(1.dp, Rule), shape = MaterialTheme.shapes.medium, tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = task.completed, enabled = !busy,
                onCheckedChange = { onToggle(task.copy(completed = it)) },
                modifier = Modifier.semantics { contentDescription = "${if (task.completed) "Buka kembali" else "Selesaikan"} tugas ${task.title}" },
            )
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None)
                courses.find { it.id == task.courseId }?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MutedInk) }
                val overdue = !task.completed && task.dueEpochDay?.let { it < today.toEpochDay() } == true
                Text(taskDeadline(task, today), style = MaterialTheme.typography.bodySmall, color = if (overdue) MaterialTheme.colorScheme.error else CampusGreen)
                if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodyMedium)
                Text("Ketuk untuk edit", style = MaterialTheme.typography.labelSmall, color = MutedInk)
            }
        }
    }
}

@Composable
fun TasksScreen(data: CampusSnapshot, today: LocalDate, busy: Boolean, onAdd: () -> Unit, onEdit: (Long) -> Unit, onToggle: (TaskEntity) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("active") }
    val tasks = data.tasks.filter {
        when (filter) { "active" -> !it.completed; "done" -> it.completed; else -> true }
    }.sortedWith(compareBy<TaskEntity> { it.completed }.thenBy { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.id })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeading("Tugas", "${data.tasks.count { !it.completed }} belum selesai · ${data.tasks.count { it.completed }} selesai") }
        item {
            Button(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Tambah tugas", Modifier.padding(start = 8.dp))
            }
        }
        item { ChoiceField("Tampilkan", filter, listOf("active" to "Belum selesai", "done" to "Selesai", "all" to "Semua tugas")) { filter = it } }
        if (tasks.isEmpty()) item {
            PaperPanel {
                Text(if (filter == "done") "Belum ada tugas selesai" else "Ruang untuk bernapas", style = MaterialTheme.typography.titleLarge)
                Text(if (data.tasks.isEmpty()) "Catat tugas pertama Anda, dengan atau tanpa tenggat." else "Tidak ada tugas pada pilihan ini. Anda bisa mengganti tampilan.")
            }
        }
        items(tasks, key = { it.id }) { task ->
            TaskRow(task, data.courses, today, busy, { onEdit(task.id) }, onToggle)
        }
        item { Text("Daftar pribadi, tersimpan offline. Notifikasi pengingat hanya untuk jadwal kuliah.", style = MaterialTheme.typography.bodySmall, color = MutedInk) }
    }
}
